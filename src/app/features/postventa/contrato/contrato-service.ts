import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { ApiService } from '../../../core/services/api-service';
import { Contrato, ContratoRequest } from './contrato.model';

@Injectable({ providedIn: 'root' })
export class ContratoService {
  private api = inject(ApiService);
  private http = inject(HttpClient);
  private endpoint = '/v1/admin/contratos';

  listar(filtro: { clienteId?: number | null; estado?: string } = {}) {
    let params = new HttpParams();
    if (filtro.clienteId) params = params.set('clienteId', filtro.clienteId);
    if (filtro.estado) params = params.set('estado', filtro.estado);
    return this.http.get<Contrato[]>(this.api.buildUrl(this.endpoint), { params });
  }

  /** Solo ADMIN: programa las visitas del contrato. */
  crear(contrato: ContratoRequest) {
    return this.http.post<Contrato>(this.api.buildUrl(this.endpoint), contrato);
  }

  /** Solo ADMIN: sus visitas pendientes se cancelan. */
  cancelar(id: number, motivo: string) {
    return this.http.post<Contrato>(this.api.buildUrl(`${this.endpoint}/${id}/cancelar`), { motivo });
  }
}
