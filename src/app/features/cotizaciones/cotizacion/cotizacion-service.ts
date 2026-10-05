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

  aceptar(id: number) {
    return this.http.post<CotizacionResponse>(this.api.buildUrl(`${this.endpoint}/${id}/aceptar`), {});
  }

  rechazar(id: number) {
    return this.http.post<CotizacionResponse>(this.api.buildUrl(`${this.endpoint}/${id}/rechazar`), {});
  }

  enviar(id: number, whatsapp: boolean = true, correo: boolean = true) {
    return this.http.post<void>(
      this.api.buildUrl(`${this.endpoint}/${id}/enviar?whatsapp=${whatsapp}&correo=${correo}`), {}
    );
  }
}
