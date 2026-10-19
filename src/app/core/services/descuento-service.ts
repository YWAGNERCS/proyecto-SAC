import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { map } from 'rxjs';
import { ApiService } from './api-service';

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
}
