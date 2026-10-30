import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl, FormBuilder, FormControl, FormGroup, FormsModule, ReactiveFormsModule,
  ValidationErrors, ValidatorFn, Validators,
} from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { forkJoin } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { CobranzaService } from './cobranza-service';
import { Cobro, CuentaPorCobrar, Devolucion, METODOS_PAGO, MetodoPago, MonedaCobro } from './cobranza.model';
import { AuthService } from '../../../core/auth/auth.service';
import { ClienteService } from '../../clientes/cliente/cliente-service';
import { Cliente } from '../../clientes/cliente/cliente.model';

type Vista = 'POR_COBRAR' | 'HISTORIAL' | 'DEVOLUCIONES';

/** Una línea del cobro: cuánto se abona a una venta del cliente (vacía = no se le abona). */
type AbonoForm = FormGroup<{
  ventaId: FormControl<number>;
  monto: FormControl<number | null>;
}>;

/** El cobro debe abonar algo a por lo menos una de las ventas listadas. */
const alMenosUnAbono: ValidatorFn = (lineas: AbstractControl): ValidationErrors | null =>
  (lineas.value as { monto: number | null }[]).some((l) => (l.monto ?? 0) > 0) ? null : { sinAbonos: true };

/** Fecha en formato yyyy-mm-dd que no pase de hoy (el backend también lo rechaza). */
const noFutura = (hoy: string): ValidatorFn => (c: AbstractControl): ValidationErrors | null =>
  c.value && c.value > hoy ? { futura: true } : null;

@Component({
  selector: 'app-cobranza-list',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './cobranza-list.html',
  styleUrl: './cobranza-list.css',
})
export class CobranzaListComponent implements OnInit {
  private fb = inject(FormBuilder);
  private cobranzaService = inject(CobranzaService);
  private clienteService = inject(ClienteService);
  private route = inject(ActivatedRoute);

  /** Registrar una devolución es solo de ADMIN (el backend lo vuelve a exigir con 403). */
  readonly esAdmin = inject(AuthService).esAdmin;
  readonly metodos = METODOS_PAGO;

  vista = signal<Vista>('POR_COBRAR');
  clienteId: number | null = null;

  // Filtros que se aplican en pantalla (sin volver a pedir al servidor)
  busqueda = signal('');
  soloVencidas = signal(false);
  desde = signal('');
  hasta = signal('');
  metodo = signal<MetodoPago | ''>('');
  clientes = signal<Cliente[]>([]);
  cuentas = signal<CuentaPorCobrar[]>([]);
  cobros = signal<Cobro[]>([]);
  devoluciones = signal<Devolucion[]>([]);
  loading = signal(false);
  error = signal('');
  mensaje = signal('');

  /** Totales por moneda: saldo por cobrar y la parte ya vencida. */
  readonly totales = computed(() =>
    (['SOLES', 'DOLARES'] as const)
      .map((moneda) => {
        const deMoneda = this.cuentas().filter((c) => c.moneda === moneda);
        return {
          moneda,
          porCobrar: deMoneda.reduce((s, c) => s + Number(c.saldo), 0),
          vencido: deMoneda.filter((c) => c.diasAtraso > 0).reduce((s, c) => s + Number(c.saldo), 0),
          cantidad: deMoneda.length,
        };
      })
      .filter((t) => t.cantidad > 0),
  );

  readonly hoy = this.isoHoy();
  private readonly inicioMes = this.hoy.slice(0, 8) + '01';

  /** Cuentas por cobrar con la búsqueda (cliente, N° de venta o comprobante) y "solo vencidas". */
  readonly cuentasFiltradas = computed(() => {
    const texto = this.busqueda().trim().toLowerCase();
    return this.cuentas().filter((c) =>
      (!this.soloVencidas() || c.diasAtraso > 0) &&
      (!texto || c.clienteNombre.toLowerCase().includes(texto) || String(c.ventaId).includes(texto)
        || (c.comprobante ?? '').toLowerCase().includes(texto)));
  });

  readonly cuentasVencidas = computed(() => this.cuentas().filter((c) => c.diasAtraso > 0).length);

  /** Antigüedad de saldos: cuánto se debe según los días de atraso, por moneda. */
  readonly antiguedad = computed(() => {
    const tramos = [
      { etiqueta: 'Al día', desde: -Infinity, hasta: 0 },
      { etiqueta: '1 a 30 días', desde: 1, hasta: 30 },
      { etiqueta: '31 a 60 días', desde: 31, hasta: 60 },
      { etiqueta: 'Más de 60 días', desde: 61, hasta: Infinity },
    ];
    return tramos.map((t, i) => {
      const enTramo = this.cuentas().filter((c) => c.diasAtraso >= t.desde && c.diasAtraso <= t.hasta);
      const suma = (m: MonedaCobro) => enTramo.filter((c) => c.moneda === m).reduce((s, c) => s + Number(c.saldo), 0);
      return { etiqueta: t.etiqueta, nivel: i, cantidad: enTramo.length, soles: suma('SOLES'), dolares: suma('DOLARES') };
    });
  });

  /** Lo cobrado en el mes en curso (fecha del pago), por moneda. */
  readonly cobradoMes = computed(() => {
    const delMes = this.cobros().filter((c) => c.estado !== 'ANULADO' && c.fecha >= this.inicioMes);
    const suma = (m: MonedaCobro) => delMes.filter((c) => c.moneda === m).reduce((s, c) => s + Number(c.montoTotal), 0);
    return { cantidad: delMes.length, soles: suma('SOLES'), dolares: suma('DOLARES') };
  });

  /** Devoluciones todavía no entregadas al cliente, por moneda. */
  readonly porDevolver = computed(() => {
    const pendientes = this.devoluciones().filter((d) => d.estado === 'PENDIENTE');
    const suma = (m: MonedaCobro) => pendientes.filter((d) => d.moneda === m).reduce((s, d) => s + Number(d.monto), 0);
    return { cantidad: pendientes.length, soles: suma('SOLES'), dolares: suma('DOLARES') };
  });

  /** Historial con el rango de fechas y el método elegidos, y su total por moneda. */
  readonly cobrosFiltrados = computed(() =>
    this.cobros().filter((c) =>
      (!this.desde() || c.fecha >= this.desde()) && (!this.hasta() || c.fecha <= this.hasta())
      && (!this.metodo() || c.metodoPago === this.metodo())));

  readonly totalHistorial = computed(() => {
    const suma = (m: MonedaCobro) => this.cobrosFiltrados().filter((c) => c.moneda === m && c.estado !== 'ANULADO')
      .reduce((s, c) => s + Number(c.montoTotal), 0);
    return { soles: suma('SOLES'), dolares: suma('DOLARES') };
  });

  guardando = signal(false);
  errorDialogo = signal('');

  // ── Diálogo "Registrar cobro": formulario reactivo cabecera-detalle (como VentaForm, S9) ──
  dialogo = signal<{ clienteNombre: string; clienteId: number; moneda: MonedaCobro } | null>(null);
  /** Ventas del diálogo: la posición i corresponde a la línea i del FormArray `abonos`. */
  cuentasDialogo: CuentaPorCobrar[] = [];

  readonly cobroForm = this.fb.nonNullable.group({
    fecha: [this.hoy, [Validators.required, noFutura(this.hoy)]],
    metodoPago: ['TRANSFERENCIA' as MetodoPago, [Validators.required]],
    referencia: ['', [Validators.maxLength(100)]],
    notas: ['', [Validators.maxLength(300)]],
    abonos: this.fb.array<AbonoForm>([], [alMenosUnAbono]),
  });

  get abonos() {
    return this.cobroForm.controls.abonos;
  }

  /** Valor de las líneas como señal, para que el total se recalcule solo. */
  private readonly valorAbonos = toSignal(this.abonos.valueChanges, { initialValue: this.abonos.getRawValue() });
  readonly totalCobro = computed(() => this.valorAbonos().reduce((s, a) => s + (Number(a.monto) || 0), 0));

  // ── Diálogo "Registrar devolución" ──
  devolucionAbierta = signal<Devolucion | null>(null);

  readonly devolucionForm = this.fb.nonNullable.group({
    fecha: [this.hoy, [Validators.required, noFutura(this.hoy)]],
    metodoPago: ['TRANSFERENCIA' as MetodoPago, [Validators.required]],
    referencia: ['', [Validators.maxLength(100)]],
    notas: ['', [Validators.maxLength(300)]],
  });

  ngOnInit() {
    // Desde Ventas se llega con ?clienteId=… para ver directamente lo que debe ese cliente
    const id = Number(this.route.snapshot.queryParamMap.get('clienteId'));
    this.clienteId = id > 0 ? id : null;
    // Desde Ventas, después de anular una venta pagada, se llega con ?vista=DEVOLUCIONES
    if (this.route.snapshot.queryParamMap.get('vista') === 'DEVOLUCIONES') this.vista.set('DEVOLUCIONES');
    // Desde los avisos de la barra, con ?vencidas=1, se abre filtrado a las cuentas vencidas
    if (this.route.snapshot.queryParamMap.get('vencidas') === '1') this.soloVencidas.set(true);
    this.clienteService.listarTodos().subscribe({ next: (data: Cliente[]) => this.clientes.set(data) });
    this.cargar();
  }

  /** Las tres pestañas ya están cargadas: cambiar de pestaña no vuelve a pedir datos. */
  cambiarVista(v: Vista) {
    this.vista.set(v);
  }

  /** Carga a la vez lo por cobrar, el historial y las devoluciones (del cliente elegido o de todos). */
  cargar() {
    this.loading.set(true);
    this.error.set('');
    forkJoin({
      cuentas: this.cobranzaService.porCobrar(this.clienteId),
      cobros: this.cobranzaService.historial(this.clienteId),
      devoluciones: this.cobranzaService.devoluciones(this.clienteId),
    }).subscribe({
      next: (r) => {
        this.cuentas.set(r.cuentas);
        this.cobros.set(r.cobros);
        this.devoluciones.set(r.devoluciones);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(this.mensajeDeError(err, 'No se pudo cargar la cobranza.'));
      },
    });
  }

  /** Saldo por cobrar (o la parte vencida) de una moneda; 0 si no hay ventas en ella. */
  totalMoneda(moneda: MonedaCobro, campo: 'porCobrar' | 'vencido'): number {
    return this.totales().find((t) => t.moneda === moneda)?.[campo] ?? 0;
  }

  limpiarFiltrosHistorial() {
    this.desde.set('');
    this.hasta.set('');
    this.metodo.set('');
  }

  /** Parte de la venta ya cobrada (0 a 100), para la barra de avance. */
  avance(c: CuentaPorCobrar): number {
    return Number(c.total) > 0 ? Math.min(100, (Number(c.cobrado) / Number(c.total)) * 100) : 0;
  }

  /**
   * Enlace de WhatsApp con el recordatorio ya escrito, o null si el cliente no tiene teléfono.
   * Lo envía la persona desde su WhatsApp: el sistema no manda nada por su cuenta.
   */
  enlaceRecordatorio(c: CuentaPorCobrar): string | null {
    const telefono = (this.clientes().find((x) => x.id === c.clienteId)?.telefono ?? '').replace(/\D/g, '');
    if (telefono.length < 9) return null;
    const numero = telefono.length === 9 ? '51' + telefono : telefono;
    const documento = c.comprobante ? `del comprobante ${c.comprobante}` : `de la venta #${c.ventaId}`;
    const vence = c.diasAtraso > 0
      ? `venció el ${c.fechaLimitePago} (${c.diasAtraso} día(s) de atraso)`
      : `vence el ${c.fechaLimitePago}`;
    const texto = `Hola, ${c.clienteNombre}. Le saludamos de JD Refrigeración. Le recordamos que el saldo de `
      + `${this.simbolo(c.moneda)} ${Number(c.saldo).toFixed(2)} ${documento} ${vence}. `
      + `Si ya realizó el pago, por favor envíenos el comprobante. ¡Gracias!`;
    return `https://wa.me/${numero}?text=${encodeURIComponent(texto)}`;
  }

  // ── Diálogo de cobro ───────────────────────────────────────

  /**
   * Abre el cobro para el cliente y la moneda de la venta elegida. Hay una línea por cada venta
   * pendiente del cliente en esa moneda; la elegida viene con su saldo completo (se puede bajar
   * para un abono parcial) y ninguna línea acepta más que su propio saldo.
   */
  abrirCobro(cuenta: CuentaPorCobrar, deduccion = false) {
    this.cobroForm.reset();
    this.abonos.clear();
    this.cuentasDialogo = this.cuentas().filter((c) => c.clienteId === cuenta.clienteId && c.moneda === cuenta.moneda);
    for (const c of this.cuentasDialogo) {
      this.abonos.push(this.fb.group({
        ventaId: this.fb.nonNullable.control(c.ventaId),
        monto: this.fb.control<number | null>(
          c.ventaId !== cuenta.ventaId ? null : deduccion ? Number(c.deduccionPendiente) : Number(c.netoPorCobrar) || Number(c.saldo),
          [Validators.min(0.01), Validators.max(Number(c.saldo))]),
      }));
    }
    // Detracción o retención: el "pago" es la constancia o el comprobante que entrega el cliente
    if (deduccion) {
      this.cobroForm.controls.metodoPago.setValue(cuenta.tipoDeduccion === 'DETRACCION' ? 'DETRACCION' : 'RETENCION');
    }
    this.errorDialogo.set('');
    this.dialogo.set({ clienteNombre: cuenta.clienteNombre, clienteId: cuenta.clienteId, moneda: cuenta.moneda });
  }

  /** Etiqueta del N° de operación según el método (constancia, comprobante o voucher). */
  etiquetaReferencia(): string {
    const m = this.cobroForm.controls.metodoPago.value;
    return m === 'DETRACCION' ? 'N° de constancia de depósito' : m === 'RETENCION' ? 'N° de comprobante de retención' : 'N° de operación (opcional)';
  }

  cerrar() {
    if (!this.guardando()) this.dialogo.set(null);
  }

  pagarTodo(i: number) {
    this.abonos.at(i).controls.monto.setValue(Number(this.cuentasDialogo[i].saldo));
  }

  confirmar() {
    const d = this.dialogo();
    if (!d) return;
    if (this.cobroForm.invalid) {
      this.cobroForm.markAllAsTouched();
      this.errorDialogo.set(this.motivoCobroInvalido());
      return;
    }
    const v = this.cobroForm.getRawValue();
    const abonos = v.abonos
      .filter((a) => (a.monto ?? 0) > 0)
      .map((a) => ({ ventaId: a.ventaId, monto: Math.round(Number(a.monto) * 100) / 100 }));
    this.guardando.set(true);
    this.errorDialogo.set('');
    this.cobranzaService.registrar({
      clienteId: d.clienteId,
      fecha: v.fecha || null,
      moneda: d.moneda,
      metodoPago: v.metodoPago,
      referencia: v.referencia.trim() || null,
      notas: v.notas.trim() || null,
      abonos,
    }).subscribe({
      next: (cobro) => {
        this.guardando.set(false);
        this.dialogo.set(null);
        const saldadas = abonos.filter((a) =>
          a.monto >= Number(this.cuentasDialogo.find((c) => c.ventaId === a.ventaId)!.saldo)).length;
        this.mensaje.set(`Cobro #${cobro.id} registrado: ${this.simbolo(d.moneda)} ${Number(cobro.montoTotal).toFixed(2)} de ${d.clienteNombre}`
          + (saldadas ? ` · ${saldadas} venta(s) quedaron pagadas` : ''));
        this.cargar();
      },
      error: (err: HttpErrorResponse) => {
        this.guardando.set(false);
        this.errorDialogo.set(this.mensajeDeError(err, 'No se pudo registrar el cobro.'));
      },
    });
  }

  /** El primer motivo por el que el cobro no es válido, dicho como lo entiende el usuario. */
  private motivoCobroInvalido(): string {
    const excedida = this.abonos.controls.findIndex((g) => g.controls.monto.hasError('max'));
    if (excedida >= 0) {
      const c = this.cuentasDialogo[excedida];
      return `El abono a la venta #${c.ventaId} supera su saldo (${Number(c.saldo).toFixed(2)}).`;
    }
    if (this.abonos.controls.some((g) => g.controls.monto.hasError('min'))) return 'Los montos abonados deben ser mayores que 0.';
    if (this.abonos.hasError('sinAbonos')) return 'Indique el monto abonado en al menos una venta.';
    if (this.cobroForm.controls.fecha.invalid) return 'Indique la fecha del pago (no puede ser futura).';
    return 'Revise los datos del cobro.';
  }

  // ── Anulación de un cobro (solo ADMIN; el backend lo vuelve a exigir con 403) ──
  anulacionAbierta = signal<Cobro | null>(null);
  readonly motivoAnulacion = this.fb.nonNullable.control('', [Validators.required, Validators.minLength(5), Validators.maxLength(300)]);

  abrirAnulacion(c: Cobro) {
    this.motivoAnulacion.reset();
    this.errorDialogo.set('');
    this.anulacionAbierta.set(c);
  }

  cerrarAnulacion() {
    if (!this.guardando()) this.anulacionAbierta.set(null);
  }

  confirmarAnulacion() {
    const c = this.anulacionAbierta();
    if (!c) return;
    if (this.motivoAnulacion.invalid) {
      this.motivoAnulacion.markAsTouched();
      this.errorDialogo.set('Indique el motivo de la anulación (al menos 5 caracteres).');
      return;
    }
    this.guardando.set(true);
    this.errorDialogo.set('');
    this.cobranzaService.anular(c.id, this.motivoAnulacion.value.trim()).subscribe({
      next: () => {
        this.guardando.set(false);
        this.anulacionAbierta.set(null);
        this.mensaje.set(`Cobro #${c.id} anulado: ${this.simbolo(c.moneda)} ${Number(c.montoTotal).toFixed(2)} vuelve al saldo de `
          + c.abonos.map((a) => `la venta #${a.ventaId}`).join(', ') + '.');
        this.cargar();
      },
      error: (err: HttpErrorResponse) => {
        this.guardando.set(false);
        this.errorDialogo.set(this.mensajeDeError(err, 'No se pudo anular el cobro.'));
      },
    });
  }

  // ── Devoluciones ────────────────────────────────────────────

  abrirDevolucion(d: Devolucion) {
    this.devolucionForm.reset();
    this.errorDialogo.set('');
    this.devolucionAbierta.set(d);
  }

  cerrarDevolucion() {
    if (!this.guardando()) this.devolucionAbierta.set(null);
  }

  confirmarDevolucion() {
    const d = this.devolucionAbierta();
    if (!d) return;
    if (this.devolucionForm.invalid) {
      this.devolucionForm.markAllAsTouched();
      this.errorDialogo.set('Indique la fecha de la devolución (no puede ser futura).');
      return;
    }
    const v = this.devolucionForm.getRawValue();
    this.guardando.set(true);
    this.errorDialogo.set('');
    this.cobranzaService.registrarDevolucion(d.id, {
      fecha: v.fecha || null,
      metodoPago: v.metodoPago,
      referencia: v.referencia.trim() || null,
      notas: v.notas.trim() || null,
    }).subscribe({
      next: () => {
        this.guardando.set(false);
        this.devolucionAbierta.set(null);
        this.mensaje.set(`Devolución registrada: ${this.simbolo(d.moneda)} ${Number(d.monto).toFixed(2)} a ${d.clienteNombre} (venta #${d.ventaId}).`);
        this.cargar();
      },
      error: (err: HttpErrorResponse) => {
        this.guardando.set(false);
        this.errorDialogo.set(this.mensajeDeError(err, 'No se pudo registrar la devolución.'));
      },
    });
  }

  // ── Ayudas ──────────────────────────────────────────────────

  simbolo(moneda: string): string {
    return moneda === 'DOLARES' ? 'US$' : 'S/';
  }

  etiquetaMetodo(m: string): string {
    return this.metodos.find((x) => x.valor === m)?.etiqueta ?? m;
  }

  private isoHoy(): string {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }

  private mensajeDeError(err: HttpErrorResponse, porDefecto: string): string {
    if (err.status === 0) return 'No hay conexión con el servidor. Revise que el backend esté encendido.';
    const errores = err.error?.errores as Record<string, string> | undefined;
    if (errores) return Object.values(errores).join(' · ');
    return err.error?.message ?? porDefecto;
  }
}
