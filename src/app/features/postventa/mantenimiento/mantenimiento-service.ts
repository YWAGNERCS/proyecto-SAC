import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { ApiService } from '../../../core/services/api-service';
import { Mantenimiento, ResumenPostventa, VistaPostventa } from './mantenimiento.model';

@Injectable({ providedIn: 'root' })
export class MantenimientoService {
  private api = inject(ApiService);
  private http = inject(HttpClient);
  private endpoint = '/v1/admin/mantenimientos';

  /** Agenda: vencidos, próximos N días, realizados o todos (opcional: de un cliente). */
  listar(vista: VistaPostventa, dias = 30, clienteId?: number | null) {
    let params = new HttpParams().set('vista', vista).set('dias', dias);
    if (clienteId) params = params.set('clienteId', clienteId);
    return this.http.get<Mantenimiento[]>(this.api.buildUrl(this.endpoint), { params });
  }

  resumen() {
    return this.http.get<ResumenPostventa>(this.api.buildUrl(`${this.endpoint}/resumen`));
  }

  /** La visita se hizo (fecha por defecto: hoy). */
  marcarRealizado(id: number, fecha: string | null, notas: string) {
    return this.http.patch<Mantenimiento>(this.api.buildUrl(`${this.endpoint}/${id}/realizado`), {
      fecha: fecha || null,
      notas: notas || null,
    });
  }

  /** El cliente pidió otra fecha (no anterior a hoy). */
  reprogramar(id: number, fecha: string, notas: string) {
    return this.http.patch<Mantenimiento>(this.api.buildUrl(`${this.endpoint}/${id}/reprogramar`), {
      fecha,
      notas: notas || null,
    });
  }
}
