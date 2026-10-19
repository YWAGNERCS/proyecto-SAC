import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { CobranzaService } from './cobranza-service';
import { Cobro, CuentaPorCobrar, METODOS_PAGO, MetodoPago, MonedaCobro } from './cobranza.model';
import { ClienteService } from '../../clientes/cliente/cliente-service';
import { Cliente } from '../../clientes/cliente/cliente.model';

/** Una línea del diálogo: cuánto se abona a cada venta del cliente. */
interface LineaAbono {
  cuenta: CuentaPorCobrar;
  monto: number | null;
}

@Component({
  selector: 'app-cobranza-list',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './cobranza-list.html',
  styleUrl: './cobranza-list.css',
})
export class CobranzaListComponent implements OnInit {
  private cobranzaService = inject(CobranzaService);
  private clienteService = inject(ClienteService);
  private route = inject(ActivatedRoute);

  readonly metodos = METODOS_PAGO;

  vista = signal<'POR_COBRAR' | 'HISTORIAL'>('POR_COBRAR');
  clienteId: number | null = null;
  clientes = signal<Cliente[]>([]);
  cuentas = signal<CuentaPorCobrar[]>([]);
  cobros = signal<Cobro[]>([]);
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

  // Diálogo "Registrar cobro"
  dialogo = signal<{ clienteNombre: string; clienteId: number; moneda: MonedaCobro } | null>(null);
  lineas: LineaAbono[] = [];
  fecha = '';
  metodoPago: MetodoPago = 'TRANSFERENCIA';
  referencia = '';
  notas = '';
  guardando = signal(false);
  errorDialogo = signal('');

  readonly hoy = this.isoHoy();

  ngOnInit() {
    // Desde Ventas se llega con ?clienteId=… para ver directamente lo que debe ese cliente
    const id = Number(this.route.snapshot.queryParamMap.get('clienteId'));
    this.clienteId = id > 0 ? id : null;
    this.clienteService.listarTodos().subscribe({ next: (data: Cliente[]) => this.clientes.set(data) });
    this.cargar();
  }

  cambiarVista(v: 'POR_COBRAR' | 'HISTORIAL') {
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
    } else {
      this.cobranzaService.historial(this.clienteId).subscribe({
        next: (data) => { this.cobros.set(data); this.loading.set(false); },
        error: fallo,
      });
    }
  }

  // ── Diálogo ─────────────────────────────────────────────────

  /**
   * Abre el cobro para el cliente y la moneda de la venta elegida. Se listan todas sus ventas
   * pendientes en esa moneda; la elegida viene con su saldo completo (se puede bajar para un abono parcial).
   */
  abrirCobro(cuenta: CuentaPorCobrar) {
    this.lineas = this.cuentas()
      .filter((c) => c.clienteId === cuenta.clienteId && c.moneda === cuenta.moneda)
      .map((c) => ({ cuenta: c, monto: c.ventaId === cuenta.ventaId ? Number(c.saldo) : null }));
    this.fecha = this.hoy;
    this.metodoPago = 'TRANSFERENCIA';
    this.referencia = '';
    this.notas = '';
    this.errorDialogo.set('');
    this.dialogo.set({ clienteNombre: cuenta.clienteNombre, clienteId: cuenta.clienteId, moneda: cuenta.moneda });
  }

  cerrar() {
    if (!this.guardando()) this.dialogo.set(null);
  }

  totalCobro(): number {
    return this.lineas.reduce((s, l) => s + (Number(l.monto) || 0), 0);
  }

  pagarTodo(l: LineaAbono) {
    l.monto = Number(l.cuenta.saldo);
  }

  confirmar() {
    const d = this.dialogo();
    if (!d) return;
    const abonos = this.lineas
      .filter((l) => Number(l.monto) > 0)
      .map((l) => ({ ventaId: l.cuenta.ventaId, monto: Math.round(Number(l.monto) * 100) / 100 }));
    if (abonos.length === 0) {
      this.errorDialogo.set('Indique el monto abonado en al menos una venta.');
      return;
    }
    const excedida = this.lineas.find((l) => Number(l.monto) > Number(l.cuenta.saldo));
    if (excedida) {
      this.errorDialogo.set(`El abono a la venta #${excedida.cuenta.ventaId} supera su saldo (${excedida.cuenta.saldo}).`);
      return;
    }
    this.guardando.set(true);
    this.errorDialogo.set('');
    this.cobranzaService.registrar({
      clienteId: d.clienteId,
      fecha: this.fecha || null,
      moneda: d.moneda,
      metodoPago: this.metodoPago,
      referencia: this.referencia.trim() || null,
      notas: this.notas.trim() || null,
      abonos,
    }).subscribe({
      next: (cobro) => {
        this.guardando.set(false);
        this.dialogo.set(null);
        const saldadas = abonos.filter((a) => {
          const cuenta = this.lineas.find((l) => l.cuenta.ventaId === a.ventaId)!.cuenta;
          return a.monto >= Number(cuenta.saldo);
        }).length;
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
