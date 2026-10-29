import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ApiService } from '../../../core/services/api-service';
import { Sede, SedeRequest } from './sede.model';

@Injectable({ providedIn: 'root' })
export class SedeService {
  private api = inject(ApiService);
  private http = inject(HttpClient);

  private url(clienteId: number, sedeId?: number) {
    return this.api.buildUrl(`/v1/admin/clientes/${clienteId}/sedes` + (sedeId ? `/${sedeId}` : ''));
  }

  listar(clienteId: number) {
    return this.http.get<Sede[]>(this.url(clienteId));
  }

  crear(clienteId: number, sede: SedeRequest) {
    return this.http.post<Sede>(this.url(clienteId), sede);
  }

  actualizar(clienteId: number, sedeId: number, sede: SedeRequest) {
    return this.http.put<Sede>(this.url(clienteId, sedeId), sede);
  }

  /** Solo ADMIN; 409 si la sede ya tiene equipos, órdenes o contratos. */
  eliminar(clienteId: number, sedeId: number) {
    return this.http.delete<void>(this.url(clienteId, sedeId));
  }
}
