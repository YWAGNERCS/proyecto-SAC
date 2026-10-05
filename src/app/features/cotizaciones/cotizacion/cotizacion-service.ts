import { Injectable, inject } from '@angular/core';
import { ApiService } from '../../../core/services/api-service';
import { HttpClient } from '@angular/common/http';
import { CotizacionResponse, CotizacionRequest } from './cotizacion.model';

@Injectable({ providedIn: 'root' })
export class CotizacionService {
  private api = inject(ApiService);
  private http = inject(HttpClient);
  private endpoint = '/admin/cotizaciones';

  listar(clienteId?: number) {
    const params = clienteId ? { clienteId } : undefined;
    return this.http.get<CotizacionResponse[]>(this.api.buildUrl(this.endpoint), { params });
  }

  crear(data: CotizacionRequest) {
    return this.http.post<CotizacionResponse>(this.api.buildUrl(this.endpoint), data);
  }
}
