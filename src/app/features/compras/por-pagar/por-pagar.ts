import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { ProveedorService } from '../../proveedores/proveedor/proveedor-service';
import { Proveedor } from '../../proveedores/proveedor/proveedor.model';
import {
  CuentaPorPagar, MetodoPagoProveedor, PagoProveedor, PorPagarService, ResumenPorPagar,
} from './por-pagar-service';

/**
 * Inventario › Cuentas por pagar: lo que JD le debe a sus proveedores por compras a crédito
 * (la que vence primero arriba), los pagos que se le hicieron y el registro de un pago nuevo.
 * Anular un pago es del administrador o de quien tenga el permiso ANULAR_COBROS.
 */
@Component({
  selector: 'app-por-pagar',
  imports: [DatePipe, DecimalPipe, FormsModule, RouterLink],
  templateUrl: './por-pagar.html',
  styleUrl: './por-pagar.css',
})
export class PorPagarComponent {
  private readonly servicio = inject(PorPagarService);
  private readonly auth = inject(AuthService);
  readonly puedeAnular = computed(() => this.auth.puede('ANULAR_COBROS'));

  readonly pestana = signal<'PENDIENTES' | 'PAGOS'>('PENDIENTES');
  readonly proveedores = signal<Proveedor[]>([]);
  proveedorId: number | null = null;
  soloVencidas = false;
  desde = '';
  hasta = '';

  readonly cuentas = signal<CuentaPorPagar[]>([]);
  readonly resumen = signal<ResumenPorPagar[]>([]);
  readonly pagos = signal<PagoProveedor[]>([]);
  readonly cargando = signal(false);
  readonly error = signal('');

  readonly metodos: { valor: MetodoPagoProveedor; etiqueta: string }[] = [
    { valor: 'TRANSFERENCIA', etiqueta: 'Transferencia' },
    { valor: 'DEPOSITO', etiqueta: 'Depósito' },
    { valor: 'EFECTIVO', etiqueta: 'Efectivo' },
    { valor: 'CHEQUE', etiqueta: 'Cheque' },
    { valor: 'YAPE_PLIN', etiqueta: 'Yape / Plin' },
  ];

  /** Diálogo de pago abierto (la cuenta a pagar). */
  readonly pagando = signal<CuentaPorPagar | null>(null);
  pago = { fechaPago: '', monto: 0, metodo: 'TRANSFERENCIA' as MetodoPagoProveedor, numeroOperacion: '', observacion: '' };
  /** Diálogo de anulación abierto. */
  readonly anulando = signal<PagoProveedor | null>(null);
  motivo = '';
  readonly guardando = signal(false);
  readonly errorDialogo = signal('');

  readonly hoy = (() => {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  })();

  constructor() {
    inject(ProveedorService).listarTodos().subscribe({ next: (l) => this.proveedores.set(l) });
    this.cargar();
  }

  cambiarPestana(p: 'PENDIENTES' | 'PAGOS') {
    this.pestana.set(p);
    this.cargar();
  }

  cargar() {
    this.cargando.set(true);
    this.error.set('');
    const fallo = (err: { error?: { message?: string } }) => {
      this.error.set(err.error?.message ?? 'No se pudo cargar la información.');
      this.cargando.set(false);
    };
    if (this.pestana() === 'PENDIENTES') {
      this.servicio.pendientes(this.proveedorId, this.soloVencidas).subscribe({
        next: (r) => {
          this.cuentas.set(r.cuentas);
          this.resumen.set(r.resumen);
          this.cargando.set(false);
        },
        error: fallo,
      });
    } else {
      this.servicio.pagos({ proveedorId: this.proveedorId, desde: this.desde, hasta: this.hasta }).subscribe({
        next: (l) => {
          this.pagos.set(l);
          this.cargando.set(false);
        },
        error: fallo,
      });
    }
  }

  simbolo(moneda: string) {
    return moneda === 'DOLARES' ? 'US$' : 'S/';
  }

  etiquetaMetodo(m: string) {
    return this.metodos.find((x) => x.valor === m)?.etiqueta ?? m;
  }

  abrirPago(c: CuentaPorPagar) {
    this.pago = { fechaPago: this.hoy, monto: c.saldo, metodo: 'TRANSFERENCIA', numeroOperacion: '', observacion: '' };
    this.errorDialogo.set('');
    this.pagando.set(c);
  }

  confirmarPago() {
    const c = this.pagando();
    if (!c) return;
    if (!this.pago.monto || this.pago.monto <= 0 || this.pago.monto > c.saldo) {
      this.errorDialogo.set(`El monto debe ser mayor que 0 y no superar el saldo (${this.simbolo(c.moneda)} ${c.saldo}).`);
      return;
    }
    if (this.pago.metodo !== 'EFECTIVO' && !this.pago.numeroOperacion.trim()) {
      this.errorDialogo.set('Indique el N° de operación (o de cheque).');
      return;
    }
    this.guardando.set(true);
    this.servicio.registrar({
      compraId: c.compraId,
      fechaPago: this.pago.fechaPago,
      monto: this.pago.monto,
      metodo: this.pago.metodo,
      numeroOperacion: this.pago.numeroOperacion.trim() || null,
      observacion: this.pago.observacion.trim() || null,
    }).subscribe({
      next: () => {
        this.guardando.set(false);
        this.pagando.set(null);
        this.cargar();
      },
      error: (err) => {
        this.guardando.set(false);
        const errores = err.error?.errores as Record<string, string> | undefined;
        this.errorDialogo.set(errores ? Object.values(errores).join(' · ') : err.error?.message ?? 'No se pudo registrar el pago.');
      },
    });
  }

  abrirAnulacion(p: PagoProveedor) {
    this.motivo = '';
    this.errorDialogo.set('');
    this.anulando.set(p);
  }

  confirmarAnulacion() {
    const p = this.anulando();
    if (!p) return;
    if (this.motivo.trim().length < 5) {
      this.errorDialogo.set('Escriba el motivo (al menos 5 caracteres).');
      return;
    }
    this.guardando.set(true);
    this.servicio.anular(p.id, this.motivo.trim()).subscribe({
      next: () => {
        this.guardando.set(false);
        this.anulando.set(null);
        this.cargar();
      },
      error: (err) => {
        this.guardando.set(false);
        this.errorDialogo.set(err.error?.message ?? 'No se pudo anular el pago.');
      },
    });
  }
}
