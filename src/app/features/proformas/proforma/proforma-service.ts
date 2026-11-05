import { Injectable, inject } from '@angular/core';
import { ApiService } from '../../../core/services/api-service';
import { HttpClient } from '@angular/common/http';
import { ProformaResponse, ProformaRequest, ResultadoEnvio } from './proforma.model';
import { Pagina } from '../../../core/services/pagina';

@Injectable({ providedIn: 'root' })
export class ProformaService {
  private api = inject(ApiService);
  private http = inject(HttpClient);
  private endpoint = '/v1/admin/proformas';

  listar(clienteId?: number) {
    const params = clienteId ? { clienteId } : undefined;
    return this.http.get<ProformaResponse[]>(this.api.buildUrl(this.endpoint), { params });
  }

  /** Una página de proformas, la más nueva primero (cliente opcional). */
  listarPagina(clienteId: number | undefined, pagina: number, tamano: number) {
    const params: Record<string, number> = { pagina, tamano };
    if (clienteId) params['clienteId'] = clienteId;
    return this.http.get<Pagina<ProformaResponse>>(this.api.buildUrl(`${this.endpoint}/pagina`), { params });
  }

  crear(data: ProformaRequest) {
    return this.http.post<ProformaResponse>(this.api.buildUrl(this.endpoint), data);
  }

  aceptar(id: number) {
    return this.http.post<ProformaResponse>(this.api.buildUrl(`${this.endpoint}/${id}/aceptar`), {});
  }

  rechazar(id: number) {
    return this.http.post<ProformaResponse>(this.api.buildUrl(`${this.endpoint}/${id}/rechazar`), {});
  }

  /** Devuelve qué canal salió y cuál no, con un mensaje para mostrar. */
  enviar(id: number, whatsapp: boolean = true, correo: boolean = true) {
    return this.http.post<ResultadoEnvio>(
      this.api.buildUrl(`${this.endpoint}/${id}/enviar?whatsapp=${whatsapp}&correo=${correo}`), {}
    );
  }
}
