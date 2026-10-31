import { Component, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../../../core/services/api-service';
import { DescargaService } from '../../../core/services/descarga-service';
import { VentaResponse } from '../../ventas/venta/venta.model';
import { IconoComponent } from '../../../core/layout/icono';

/** Refleja VentaReporteService.HistorialCliente del backend. */
interface HistorialCliente {
  clienteId: number;
  nombre: string;
  documento: string;
  tipo: string;
  cantidadVentas: number;
  cantidadAnuladas: number;
  totalSoles: number;
  totalDolares: number;
  pendienteSoles: number;
  pendienteDolares: number;
  primeraCompra: string | null;
  ultimaCompra: string | null;
  ventas: VentaResponse[];
}

@Component({
  selector: 'app-cliente-historial',
  imports: [IconoComponent, RouterLink, DecimalPipe],
  templateUrl: './cliente-historial.html',
})
export class ClienteHistorialComponent {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiService);
  private readonly descarga = inject(DescargaService);
  private readonly route = inject(ActivatedRoute);

  protected readonly historial = signal<HistorialCliente | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly exportando = signal(false);
  private readonly id = Number(this.route.snapshot.paramMap.get('id'));

  constructor() {
    this.http.get<HistorialCliente>(this.api.buildUrl(`/v1/admin/ventas/historial-cliente/${this.id}`)).subscribe({
      next: (h) => this.historial.set(h),
      error: (err) => this.error.set(err.status === 404 ? 'El cliente no existe.' : 'No se pudo cargar el historial.'),
    });
  }

  exportarExcel(): void {
    this.exportando.set(true);
    this.descarga
      .descargar('/v1/admin/ventas/exportar', `ventas_cliente_${this.id}_${this.descarga.hoy()}.xlsx`, { clienteId: this.id })
      .subscribe({
        next: () => this.exportando.set(false),
        error: () => {
          this.exportando.set(false);
          this.error.set('No se pudo generar el Excel.');
        },
      });
  }

  descargarComprobante(v: VentaResponse): void {
    this.descarga.descargar(`/v1/admin/facturacion/ventas/${v.id}/pdf`, `comprobante_venta_${v.id}.pdf`).subscribe({
      error: () => this.error.set(`No se pudo descargar el comprobante de la venta #${v.id}.`),
    });
  }

  protected simbolo(moneda: string): string {
    return moneda === 'DOLARES' ? 'US$' : 'S/';
  }
}
