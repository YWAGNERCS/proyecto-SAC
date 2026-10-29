import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { ApiService } from '../../../core/services/api-service';
import { Cobro, CobroRequest, CuentaPorCobrar, Devolucion, DevolucionRequest, PagadoDeVenta } from './cobranza.model';

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

  /** Cuánto pagó el cliente de una venta: se avisa antes de anularla. */
  /** Anula un cobro mal registrado (solo ADMIN): deja de contar en el saldo de sus ventas. */
  anular(id: number, motivo: string) {
    return this.http.post<Cobro>(this.api.buildUrl(`${this.endpoint}/cobros/${id}/anular`), { motivo });
  }

  pagadoDeVenta(ventaId: number) {
    return this.http.get<PagadoDeVenta>(this.api.buildUrl(`${this.endpoint}/ventas/${ventaId}/pagado`));
  }

  /** Devoluciones por ventas anuladas (opcional: de un cliente), primero las pendientes. */
  devoluciones(clienteId?: number | null) {
    let params = new HttpParams();
    if (clienteId) params = params.set('clienteId', clienteId);
    return this.http.get<Devolucion[]>(this.api.buildUrl(`${this.endpoint}/devoluciones`), { params });
  }

  /** PENDIENTE -> DEVUELTA (solo ADMIN). */
  registrarDevolucion(id: number, request: DevolucionRequest) {
    return this.http.post<Devolucion>(this.api.buildUrl(`${this.endpoint}/devoluciones/${id}/registrar`), request);
  }
}
