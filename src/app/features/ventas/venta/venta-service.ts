import { Injectable, inject } from '@angular/core';
import { ApiService } from '../../../core/services/api-service';
import { HttpClient } from '@angular/common/http';
import { VentaResponse, VentaDirectaRequest } from './venta.model';

@Injectable({ providedIn: 'root' })
export class VentaService {
  private api = inject(ApiService);
  private http = inject(HttpClient);
  private endpoint = '/admin/ventas';

  listar() {
    return this.http.get<VentaResponse[]>(this.api.buildUrl(this.endpoint));
  }

  convertirDesdeCotizacion(cotizacionId: number, tipoPago: string, diasCredito?: number) {
    let url = `${this.api.buildUrl(this.endpoint)}/desde-cotizacion/${cotizacionId}?tipoPago=${tipoPago}`;
    if (diasCredito) {
      url += `&diasCredito=${diasCredito}`;
    }
    return this.http.post<VentaResponse>(url, {});
  }

  registrarVentaDirecta(request: VentaDirectaRequest) {
    return this.http.post<VentaResponse>(this.api.buildUrl(`${this.endpoint}/directa`), request);
  }

  listarPagosAtrasados() {
    return this.http.get<VentaResponse[]>(this.api.buildUrl(`${this.endpoint}/pagos-atrasados`));
  }

  marcarComoPagada(id: number) {
    return this.http.post<VentaResponse>(this.api.buildUrl(`${this.endpoint}/${id}/marcar-pagada`), {});
  }
}
