import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { ApiService } from '../../../core/services/api-service';
import { Cobro, CobroRequest, CuentaPorCobrar } from './cobranza.model';

@Injectable({ providedIn: 'root' })
export class CobranzaService {
  private api = inject(ApiService);
  private http = inject(HttpClient);
  private endpoint = '/v1/admin/cobranza';

  /** Ventas a crédito con saldo (opcional: de un cliente), primero las que vencen antes. */
  porCobrar(clienteId?: number | null) {
    let params = new HttpParams();
    if (clienteId) params = params.set('clienteId', clienteId);
    return this.http.get<CuentaPorCobrar[]>(this.api.buildUrl(`${this.endpoint}/por-cobrar`), { params });
  }

  historial(clienteId?: number | null) {
    let params = new HttpParams();
    if (clienteId) params = params.set('clienteId', clienteId);
    return this.http.get<Cobro[]>(this.api.buildUrl(`${this.endpoint}/cobros`), { params });
  }

  registrar(request: CobroRequest) {
    return this.http.post<Cobro>(this.api.buildUrl(`${this.endpoint}/cobros`), request);
  }
}
