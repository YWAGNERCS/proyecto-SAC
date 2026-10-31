import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpParams } from '@angular/common/http';
import { ApiService } from '../../../core/services/api-service';

interface Fila {
  clave: string;
  nombre: string;
  detalle: string | null;
  cantidad: number;
  ingresos: number;
  costos: number;
  margen: number;
  margenPorcentaje: number;
}

interface VentaFila {
  ventaId: number;
  fecha: string;
  cliente: string;
  vendedor: string | null;
  moneda: string;
  ingresos: number;
  costos: number;
  margen: number;
  margenPorcentaje: number;
  incompleta: boolean;
}

interface TecnicoFila {
  tecnico: string;
  ordenes: number;
  facturadas: number;
  ingresos: number;
  costoManoObra: number;
  costoMateriales: number;
  margen: number;
  margenPorcentaje: number;
}

interface Rentabilidad {
  resumen: { ventas: number; ingresos: number; costos: number; margen: number; margenPorcentaje: number; lineasSinCosto: number };
  porVenta: VentaFila[];
  porCliente: Fila[];
  porProducto: Fila[];
  porCategoria: Fila[];
  porTecnico: TecnicoFila[];
}

type Vista = 'VENTA' | 'CLIENTE' | 'PRODUCTO' | 'CATEGORIA' | 'TECNICO';

/**
 * Comercial › Rentabilidad (ADMIN o permiso VER_RENTABILIDAD): cuánto se ganó de verdad.
 * Ingreso = precio sin IGV con descuento; costo = costo del producto al vender. Todo en soles
 * (lo vendido en dólares, al tipo de cambio de cada venta).
 */
@Component({
  selector: 'app-rentabilidad',
  imports: [DatePipe, DecimalPipe, FormsModule],
  templateUrl: './rentabilidad.html',
  styleUrl: './rentabilidad.css',
})
export class RentabilidadComponent {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiService);

  desde = '';
  hasta = '';
  readonly vista = signal<Vista>('VENTA');
  readonly datos = signal<Rentabilidad | null>(null);
  readonly cargando = signal(false);
  readonly error = signal('');

  readonly vistas: { valor: Vista; etiqueta: string }[] = [
    { valor: 'VENTA', etiqueta: 'Por venta' },
    { valor: 'CLIENTE', etiqueta: 'Por cliente' },
    { valor: 'PRODUCTO', etiqueta: 'Por producto' },
    { valor: 'CATEGORIA', etiqueta: 'Por categoría' },
    { valor: 'TECNICO', etiqueta: 'Por técnico' },
  ];

  /** Filas agrupadas de la vista actual (cliente, producto o categoría). */
  readonly filas = computed<Fila[]>(() => {
    const d = this.datos();
    if (!d) return [];
    return this.vista() === 'CLIENTE' ? d.porCliente : this.vista() === 'PRODUCTO' ? d.porProducto : d.porCategoria;
  });

  /** Para la barra de cada fila: el mayor margen de la vista. */
  readonly maximo = computed(() => Math.max(1, ...this.filas().map((f) => Math.abs(f.margen))));

  constructor() {
    const hoy = new Date();
    const dos = (n: number) => String(n).padStart(2, '0');
    this.desde = `${hoy.getFullYear()}-${dos(hoy.getMonth() + 1)}-01`;
    this.hasta = `${hoy.getFullYear()}-${dos(hoy.getMonth() + 1)}-${dos(hoy.getDate())}`;
    this.cargar();
  }

  /** Atajos de periodo. */
  periodo(tipo: 'MES' | 'TRIMESTRE' | 'ANIO') {
    const hoy = new Date();
    const dos = (n: number) => String(n).padStart(2, '0');
    const inicio = tipo === 'MES' ? new Date(hoy.getFullYear(), hoy.getMonth(), 1)
      : tipo === 'TRIMESTRE' ? new Date(hoy.getFullYear(), Math.floor(hoy.getMonth() / 3) * 3, 1)
      : new Date(hoy.getFullYear(), 0, 1);
    this.desde = `${inicio.getFullYear()}-${dos(inicio.getMonth() + 1)}-01`;
    this.hasta = `${hoy.getFullYear()}-${dos(hoy.getMonth() + 1)}-${dos(hoy.getDate())}`;
    this.cargar();
  }

  cargar() {
    this.cargando.set(true);
    this.error.set('');
    let params = new HttpParams();
    if (this.desde) params = params.set('desde', this.desde);
    if (this.hasta) params = params.set('hasta', this.hasta);
    this.http.get<Rentabilidad>(this.api.buildUrl('/v1/admin/reportes/rentabilidad'), { params }).subscribe({
      next: (d) => {
        this.datos.set(d);
        this.cargando.set(false);
      },
      error: (err) => {
        this.error.set(err.error?.message ?? 'No se pudo calcular la rentabilidad.');
        this.cargando.set(false);
      },
    });
  }

  /** Margen bajo (menos de 10 %) o pérdida: se marca para revisarlo. */
  bajo(porcentaje: number) {
    return porcentaje < 10;
  }

  ancho(margen: number) {
    return `${Math.max(2, (Math.abs(margen) / this.maximo()) * 100)}%`;
  }
}
