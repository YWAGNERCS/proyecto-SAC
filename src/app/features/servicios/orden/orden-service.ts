import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { ApiService } from '../../../core/services/api-service';
import { CierreRequest, EstadoOrden, FacturacionRequest, OrdenRequest, OrdenTrabajo } from './orden.model';

@Injectable({ providedIn: 'root' })
export class OrdenService {
  private api = inject(ApiService);
  private http = inject(HttpClient);
  private endpoint = '/v1/admin/ordenes';

  /** Solo se envían los filtros que tienen valor. */
  listar(filtro: { estado?: EstadoOrden | ''; clienteId?: number | null; tecnico?: string; desde?: string; hasta?: string } = {}) {
    let params = new HttpParams();
    if (filtro.estado) params = params.set('estado', filtro.estado);
    if (filtro.clienteId) params = params.set('clienteId', filtro.clienteId);
    if (filtro.tecnico?.trim()) params = params.set('tecnico', filtro.tecnico.trim());
    if (filtro.desde) params = params.set('desde', filtro.desde);
    if (filtro.hasta) params = params.set('hasta', filtro.hasta);
    return this.http.get<OrdenTrabajo[]>(this.api.buildUrl(this.endpoint), { params });
  }

  obtener(id: number) {
    return this.http.get<OrdenTrabajo>(this.api.buildUrl(`${this.endpoint}/${id}`));
  }

  /** Agenda de técnicos: órdenes no canceladas entre dos fechas (hasta 6 semanas). */
  agenda(desde: string, hasta: string) {
    const params = new HttpParams().set('desde', desde).set('hasta', hasta);
    return this.http.get<OrdenTrabajo[]>(this.api.buildUrl(`${this.endpoint}/agenda`), { params });
  }

  tecnicos() {
    return this.http.get<string[]>(this.api.buildUrl(`${this.endpoint}/tecnicos`));
  }

  crear(orden: OrdenRequest) {
    return this.http.post<OrdenTrabajo>(this.api.buildUrl(this.endpoint), orden);
  }

  actualizar(id: number, orden: OrdenRequest) {
    return this.http.put<OrdenTrabajo>(this.api.buildUrl(`${this.endpoint}/${id}`), orden);
  }

  cerrar(id: number, cierre: CierreRequest) {
    return this.http.post<OrdenTrabajo>(this.api.buildUrl(`${this.endpoint}/${id}/cerrar`), cierre);
  }

  /** Solo ADMIN. */
  cancelar(id: number, motivo: string) {
    return this.http.post<OrdenTrabajo>(this.api.buildUrl(`${this.endpoint}/${id}/cancelar`), { motivo });
  }

  facturar(id: number, factura: FacturacionRequest) {
    return this.http.post<OrdenTrabajo>(this.api.buildUrl(`${this.endpoint}/${id}/facturar`), factura);
  }
}
