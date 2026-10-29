import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { ApiService } from '../../../core/services/api-service';
import { Equipo, EquipoEdicion, EquipoRegistro } from './equipo.model';

@Injectable({ providedIn: 'root' })
export class EquipoService {
  private api = inject(ApiService);
  private http = inject(HttpClient);
  private endpoint = '/v1/admin/equipos';

  listar(filtro: { clienteId?: number | null; sedeId?: number | null; estado?: string; enGarantia?: boolean | null } = {}) {
    let params = new HttpParams();
    if (filtro.clienteId) params = params.set('clienteId', filtro.clienteId);
    if (filtro.sedeId) params = params.set('sedeId', filtro.sedeId);
    if (filtro.estado) params = params.set('estado', filtro.estado);
    if (filtro.enGarantia !== null && filtro.enGarantia !== undefined) params = params.set('enGarantia', filtro.enGarantia);
    return this.http.get<Equipo[]>(this.api.buildUrl(this.endpoint), { params });
  }

  registrar(equipo: EquipoRegistro) {
    return this.http.post<Equipo>(this.api.buildUrl(this.endpoint), equipo);
  }

  actualizar(id: number, equipo: EquipoEdicion) {
    return this.http.put<Equipo>(this.api.buildUrl(`${this.endpoint}/${id}`), equipo);
  }

  /** Solo ADMIN: queda en el historial y sus visitas pendientes se cancelan. */
  retirar(id: number, motivo: string) {
    return this.http.post<Equipo>(this.api.buildUrl(`${this.endpoint}/${id}/retirar`), { motivo });
  }
}
