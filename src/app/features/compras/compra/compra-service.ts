import { Injectable, inject } from '@angular/core';
import { ApiService } from '../../../core/services/api-service';
import { HttpClient, HttpParams } from '@angular/common/http';
import { CompraRequest, CompraResponse } from './compra.model';

@Injectable({ providedIn: 'root' })
export class CompraService {
  private api = inject(ApiService);
  private http = inject(HttpClient);
  private endpoint = '/v1/admin/compras';

  /** Compras con filtros opcionales: fechas (aaaa-mm-dd) y proveedor. */
  listar(filtro: { desde?: string; hasta?: string; proveedorId?: number | null } = {}) {
    let params = new HttpParams();
    if (filtro.desde) params = params.set('desde', filtro.desde);
    if (filtro.hasta) params = params.set('hasta', filtro.hasta);
    if (filtro.proveedorId) params = params.set('proveedorId', filtro.proveedorId);
    return this.http.get<CompraResponse[]>(this.api.buildUrl(this.endpoint), { params });
  }

  buscarPorId(id: number) {
    return this.http.get<CompraResponse>(this.api.buildUrl(`${this.endpoint}/${id}`));
  }

  registrar(data: CompraRequest) {
    return this.http.post<CompraResponse>(this.api.buildUrl(this.endpoint), data);
  }
}
