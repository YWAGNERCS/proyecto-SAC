import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { ProductoService } from '../../catalogo/producto/producto-service';
import { Producto, controlaStock } from '../../catalogo/producto/producto.model';
import { AjusteInventario, AjusteService } from './ajuste-service';

/**
 * Inventario › Ajustes de inventario: historial de ajustes y registro de uno nuevo.
 *  - Conteo físico: se escribe cuántas unidades hay y el sistema calcula la diferencia.
 *  - Otro movimiento: entrada o salida con su motivo (merma, dañado, robo, stock inicial...).
 * Solo materiales (los equipos no controlan stock). Registrar: ADMIN o permiso AJUSTAR_INVENTARIO.
 */
@Component({
  selector: 'app-ajuste-list',
  imports: [DatePipe, FormsModule, RouterLink],
  templateUrl: './ajuste-list.html',
  styleUrl: './ajuste-list.css',
})
export class AjusteListComponent {
  private readonly servicio = inject(AjusteService);
  private readonly auth = inject(AuthService);
  readonly puedeAjustar = computed(() => this.auth.puede('AJUSTAR_INVENTARIO'));

  readonly materiales = signal<Producto[]>([]);
  readonly motivos = signal<{ codigo: string; nombre: string }[]>([]);
  readonly ajustes = signal<AjusteInventario[]>([]);
  readonly cargando = signal(false);
  readonly error = signal('');

  filtro = { productoId: null as number | null, motivo: '', desde: '', hasta: '' };

  readonly hoy = (() => {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  })();

  /** Formulario abierto. */
  readonly abierto = signal(false);
  modo: 'CONTEO' | 'MOVIMIENTO' = 'CONTEO';
  nuevo = this.vacio();
  readonly guardando = signal(false);
  readonly errorForm = signal('');
  readonly aviso = signal('');

  constructor() {
    inject(ProductoService).listarTodos().subscribe({
      next: (l) => this.materiales.set(l.filter((p) => controlaStock(p.sector)).sort((a, b) => a.nombre.localeCompare(b.nombre))),
    });
    this.servicio.motivos().subscribe({ next: (m) => this.motivos.set(m) });
    this.buscar();
  }

  private vacio() {
    return { productoId: null as number | null, fecha: this.hoy, stockContado: null as number | null,
      tipo: 'SALIDA' as 'ENTRADA' | 'SALIDA', cantidad: 1, motivo: 'MERMA', observacion: '' };
  }

  /** Motivos de un movimiento (el conteo físico va aparte). */
  readonly motivosMovimiento = computed(() => this.motivos().filter((m) => m.codigo !== 'CONTEO_FISICO'));

  productoElegido(): Producto | undefined {
    return this.materiales().find((p) => p.id === this.nuevo.productoId);
  }

  /** Vista previa: cómo queda el stock. */
  stockResultante(): number | null {
    const p = this.productoElegido();
    if (!p) return null;
    const actual = p.stockActual ?? 0;
    if (this.modo === 'CONTEO') return this.nuevo.stockContado;
    return this.nuevo.tipo === 'ENTRADA' ? actual + (this.nuevo.cantidad || 0) : actual - (this.nuevo.cantidad || 0);
  }

  buscar() {
    this.cargando.set(true);
    this.error.set('');
    this.servicio.buscar(this.filtro).subscribe({
      next: (l) => {
        this.ajustes.set(l);
        this.cargando.set(false);
      },
      error: (err) => {
        this.error.set(err.error?.message ?? 'No se pudieron cargar los ajustes.');
        this.cargando.set(false);
      },
    });
  }

  abrir(modo: 'CONTEO' | 'MOVIMIENTO') {
    this.modo = modo;
    this.nuevo = this.vacio();
    this.errorForm.set('');
    this.aviso.set('');
    this.abierto.set(true);
  }

  guardar() {
    const n = this.nuevo;
    if (!n.productoId) {
      this.errorForm.set('Elija el material.');
      return;
    }
    if (this.modo === 'CONTEO' && (n.stockContado === null || n.stockContado < 0)) {
      this.errorForm.set('Escriba cuántas unidades contó (0 o más).');
      return;
    }
    if (this.modo === 'MOVIMIENTO' && (!n.cantidad || n.cantidad < 1 || !n.observacion.trim())) {
      this.errorForm.set('Indique la cantidad y explique el ajuste en la observación.');
      return;
    }
    const resultado = this.stockResultante();
    if (resultado !== null && resultado < 0) {
      this.errorForm.set('La salida dejaría el stock en negativo.');
      return;
    }
    this.guardando.set(true);
    this.servicio.registrar({
      productoId: n.productoId,
      motivo: this.modo === 'CONTEO' ? 'CONTEO_FISICO' : n.motivo,
      fecha: n.fecha,
      stockContado: this.modo === 'CONTEO' ? n.stockContado : null,
      tipo: this.modo === 'MOVIMIENTO' ? n.tipo : null,
      cantidad: this.modo === 'MOVIMIENTO' ? n.cantidad : null,
      observacion: n.observacion.trim() || null,
    }).subscribe({
      next: (a) => {
        this.guardando.set(false);
        this.abierto.set(false);
        this.aviso.set(`Ajuste #${a.id}: ${a.producto} pasó de ${a.stockAnterior} a ${a.stockNuevo} unidades.`);
        // El stock del material cambió: se refresca la lista para la próxima vista previa
        this.materiales.update((l) => l.map((p) => (p.id === a.productoId ? { ...p, stockActual: a.stockNuevo } : p)));
        this.buscar();
      },
      error: (err) => {
        this.guardando.set(false);
        const errores = err.error?.errores as Record<string, string> | undefined;
        this.errorForm.set(errores ? Object.values(errores).join(' · ') : err.error?.message ?? 'No se pudo registrar el ajuste.');
      },
    });
  }
}
