import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl, FormBuilder, FormControl, FormGroup, FormsModule, ReactiveFormsModule,
  ValidationErrors, ValidatorFn, Validators,
} from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
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
    this.clienteService.listarTodos().subscribe({ next: (data: Cliente[]) => this.clientes.set(data) });
    this.cargar();
  }

  cambiarVista(v: Vista) {
    this.vista.set(v);
    this.cargar();
  }

  cargar() {
    this.loading.set(true);
    this.error.set('');
    const fallo = (err: HttpErrorResponse) => {
      this.loading.set(false);
      this.error.set(this.mensajeDeError(err, 'No se pudo cargar la cobranza.'));
    };
    if (this.vista() === 'POR_COBRAR') {
      this.cobranzaService.porCobrar(this.clienteId).subscribe({
        next: (data) => { this.cuentas.set(data); this.loading.set(false); },
        error: fallo,
      });
    } else if (this.vista() === 'HISTORIAL') {
      this.cobranzaService.historial(this.clienteId).subscribe({
        next: (data) => { this.cobros.set(data); this.loading.set(false); },
        error: fallo,
      });
    } else {
      this.cobranzaService.devoluciones(this.clienteId).subscribe({
        next: (data) => { this.devoluciones.set(data); this.loading.set(false); },
        error: fallo,
      });
    }
  }

  // ── Diálogo de cobro ───────────────────────────────────────

  /**
   * Abre el cobro para el cliente y la moneda de la venta elegida. Hay una línea por cada venta
   * pendiente del cliente en esa moneda; la elegida viene con su saldo completo (se puede bajar
   * para un abono parcial) y ninguna línea acepta más que su propio saldo.
   */
  abrirCobro(cuenta: CuentaPorCobrar) {
    this.cobroForm.reset();
    this.abonos.clear();
    this.cuentasDialogo = this.cuentas().filter((c) => c.clienteId === cuenta.clienteId && c.moneda === cuenta.moneda);
    for (const c of this.cuentasDialogo) {
      this.abonos.push(this.fb.group({
        ventaId: this.fb.nonNullable.control(c.ventaId),
        monto: this.fb.control<number | null>(c.ventaId === cuenta.ventaId ? Number(c.saldo) : null,
          [Validators.min(0.01), Validators.max(Number(c.saldo))]),
      }));
    }
    this.errorDialogo.set('');
    this.dialogo.set({ clienteNombre: cuenta.clienteNombre, clienteId: cuenta.clienteId, moneda: cuenta.moneda });
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
