import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { map } from 'rxjs';
import { ApiService } from './api-service';

export interface TopesDescuento {
  /** Tope del administrador y de quien tenga el permiso "Descuento ampliado" (%). */
  topeAdmin: number;
  /** Tope de los asistentes (%). */
  topeAsistente: number;
  /** null mientras sigan los topes iniciales. */
  actualizadoPor: string | null;
  fechaActualizacion: string | null;
}

/** Descuento máximo por línea (%) del usuario actual; el backend vuelve a validarlo. */
@Injectable({ providedIn: 'root' })
export class DescuentoService {
  private api = inject(ApiService);
  private http = inject(HttpClient);

  tope() {
    return this.http
      .get<{ topePorcentaje: number }>(this.api.buildUrl('/v1/admin/descuentos/tope'))
      .pipe(map((r) => Number(r.topePorcentaje)));
  }

  /** Los dos topes (administrador y asistente) y quién los cambió por última vez. */
  topes() {
    return this.http.get<TopesDescuento>(this.api.buildUrl('/v1/admin/descuentos/topes'));
  }

  /** Solo el administrador (el backend responde 403 a cualquier otro). Queda en el historial de cambios. */
  cambiarTopes(topeAdmin: number, topeAsistente: number) {
    return this.http.put<TopesDescuento>(this.api.buildUrl('/v1/admin/descuentos/topes'), { topeAdmin, topeAsistente });
  }
}
