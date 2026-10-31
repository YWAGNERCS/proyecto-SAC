import { Component, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpParams } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../../../core/services/api-service';
import { DescargaService } from '../../../core/services/descarga-service';
import { IconoComponent } from '../../../core/layout/icono';

/** Refleja KardexService.Kardex del backend. */
interface Movimiento {
  fecha: string;
  tipo: 'ENTRADA' | 'SALIDA' | 'DEVOLUCION' | 'AJUSTE_ENTRADA' | 'AJUSTE_SALIDA';
  documento: string;
  tercero: string;
  cantidad: number;
  precioUnitario: number | null;
  moneda: string;
  saldo: number | null;
}

interface Kardex {
  productoId: number;
  producto: string;
  categoria: string | null;
  sector: string;
  controlaStock: boolean;
  stockActual: number | null;
  stockMinimo: number | null;
  saldoInicial: number | null;
  totalEntradas: number;
  totalSalidas: number;
  totalDevoluciones: number;
  movimientos: Movimiento[];
}

@Component({
  selector: 'app-producto-kardex',
  imports: [IconoComponent, RouterLink, FormsModule, DecimalPipe],
  templateUrl: './producto-kardex.html',
})
export class ProductoKardexComponent {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiService);
  private readonly descarga = inject(DescargaService);
  private readonly route = inject(ActivatedRoute);

  protected readonly kardex = signal<Kardex | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly exportando = signal(false);
  private readonly id = Number(this.route.snapshot.paramMap.get('id'));

  // Filtro por fechas: se aplica al pulsar "Buscar"
  protected desde = '';
  protected hasta = '';

  constructor() {
    this.buscar();
  }

  buscar(): void {
    this.error.set(null);
    if (this.desde && this.hasta && this.desde > this.hasta) {
      this.error.set('La fecha "desde" no puede ser posterior a "hasta".');
      return;
    }
    let params = new HttpParams();
    if (this.desde) params = params.set('desde', this.desde);
    if (this.hasta) params = params.set('hasta', this.hasta);
    this.http.get<Kardex>(this.api.buildUrl(`/v1/admin/inventario/kardex/${this.id}`), { params }).subscribe({
      next: (k) => this.kardex.set(k),
      error: (err) => this.error.set(err.status === 404 ? 'El producto no existe.' : 'No se pudo cargar el kardex.'),
    });
  }

  limpiar(): void {
    this.desde = '';
    this.hasta = '';
    this.buscar();
  }

  exportarExcel(): void {
    this.exportando.set(true);
    this.descarga
      .descargar(`/v1/admin/inventario/kardex/${this.id}/exportar`, `kardex_producto_${this.id}_${this.descarga.hoy()}.xlsx`,
        { desde: this.desde, hasta: this.hasta })
      .subscribe({
        next: () => this.exportando.set(false),
        error: () => {
          this.exportando.set(false);
          this.error.set('No se pudo generar el Excel.');
        },
      });
  }

  /** Resta stock: venta, material de orden o ajuste de salida. */
  protected resta(tipo: Movimiento['tipo']): boolean {
    return tipo === 'SALIDA' || tipo === 'AJUSTE_SALIDA';
  }

  protected etiqueta(tipo: Movimiento['tipo']): string {
    return { ENTRADA: 'Entrada', SALIDA: 'Salida', DEVOLUCION: 'Devolución', AJUSTE_ENTRADA: 'Ajuste +', AJUSTE_SALIDA: 'Ajuste −' }[tipo];
  }

  protected simbolo(moneda: string): string {
    return moneda === 'DOLARES' ? 'US$' : 'S/';
  }
}
