import { Component, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../../../core/services/api-service';
import { DescargaService } from '../../../core/services/descarga-service';
import { CompraResponse } from '../../compras/compra/compra.model';

/** Refleja CompraReporteService.HistorialProveedor del backend. */
interface HistorialProveedor {
  proveedorId: number;
  nombre: string;
  documento: string;
  tipo: string;
  cantidadCompras: number;
  totalSoles: number;
  totalDolares: number;
  primeraCompra: string | null;
  ultimaCompra: string | null;
  compras: CompraResponse[];
}

@Component({
  selector: 'app-proveedor-historial',
  imports: [RouterLink, DecimalPipe],
  templateUrl: './proveedor-historial.html',
})
export class ProveedorHistorialComponent {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiService);
  private readonly descarga = inject(DescargaService);
  private readonly route = inject(ActivatedRoute);

  protected readonly historial = signal<HistorialProveedor | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly exportando = signal(false);
  private readonly id = Number(this.route.snapshot.paramMap.get('id'));

  constructor() {
    this.http.get<HistorialProveedor>(this.api.buildUrl(`/v1/admin/compras/historial-proveedor/${this.id}`)).subscribe({
      next: (h) => this.historial.set(h),
      error: (err) => this.error.set(err.status === 404 ? 'El proveedor no existe.' : 'No se pudo cargar el historial.'),
    });
  }

  exportarExcel(): void {
    this.exportando.set(true);
    this.descarga
      .descargar('/v1/admin/compras/exportar', `compras_proveedor_${this.id}_${this.descarga.hoy()}.xlsx`, { proveedorId: this.id })
      .subscribe({
        next: () => this.exportando.set(false),
        error: () => {
          this.exportando.set(false);
          this.error.set('No se pudo generar el Excel.');
        },
      });
  }

  protected unidades(c: CompraResponse): number {
    return c.detalles.reduce((suma, d) => suma + d.cantidad, 0);
  }

  protected simbolo(moneda: string): string {
    return moneda === 'DOLARES' ? 'US$' : 'S/';
  }
}
