import { Injectable, inject } from '@angular/core';
import { ApiService } from '../../../core/services/api-service';
import { HttpClient, HttpParams } from '@angular/common/http';
import { VentaResponse, VentaDirectaRequest, FiltroVentas } from './venta.model';

@Injectable({ providedIn: 'root' })
export class VentaService {
  private api = inject(ApiService);
  private http = inject(HttpClient);
  private endpoint = '/v1/admin/ventas';

  /** Reporte de ventas con su detalle; solo se envían los filtros que tienen valor. */
  listar(filtro: FiltroVentas = {}) {
    let params = new HttpParams();
    if (filtro.estado) params = params.set('estado', filtro.estado);
    if (filtro.desde) params = params.set('desde', filtro.desde);
    if (filtro.hasta) params = params.set('hasta', filtro.hasta);
    return this.http.get<VentaResponse[]>(this.api.buildUrl(this.endpoint), { params });
  }

  convertirDesdeProforma(proformaId: number, tipoPago: string, diasCredito?: number) {
    let url = `${this.api.buildUrl(this.endpoint)}/desde-proforma/${proformaId}?tipoPago=${tipoPago}`;
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

  /** REGISTRADA -> ANULADA. Body vacío: el backend ya sabe qué stock devolver. */
  anular(id: number) {
    return this.http.patch<VentaResponse>(this.api.buildUrl(`${this.endpoint}/${id}/anular`), {});
  }
}
