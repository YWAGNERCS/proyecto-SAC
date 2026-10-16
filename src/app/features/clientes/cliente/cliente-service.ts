import { Injectable, inject } from '@angular/core';
import { ApiService } from '../../../core/services/api-service';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Cliente } from './cliente.model';

@Injectable({ providedIn: 'root' })
export class ClienteService {
  private api = inject(ApiService);
  private http = inject(HttpClient);
  private endpoint = '/v1/admin/clientes';

  listarTodos(): Observable<Cliente[]> {
    return this.http.get<Cliente[]>(this.api.buildUrl(this.endpoint));
  }

  obtener(id: number): Observable<Cliente> {
    return this.http.get<Cliente>(this.api.buildUrl(`${this.endpoint}/${id}`));
  }

  crear(cliente: Cliente): Observable<Cliente> {
    return this.http.post<Cliente>(this.api.buildUrl(this.endpoint), cliente);
  }

  actualizar(id: number, cliente: Cliente): Observable<Cliente> {
    return this.http.put<Cliente>(this.api.buildUrl(`${this.endpoint}/${id}`), cliente);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(this.api.buildUrl(`${this.endpoint}/${id}`));
  }

  /**
   * Trae razón social y dirección desde SUNAT (no guarda nada).
   * Si el RUC ya está registrado como cliente, el backend devuelve ese registro (con id).
   */
  autocompletarRuc(ruc: string): Observable<Cliente> {
    return this.http.get<Cliente>(this.api.buildUrl(`${this.endpoint}/autocompletar/ruc/${ruc}`));
  }

  /** Trae nombres y apellidos desde RENIEC (no guarda nada). */
  autocompletarDni(dni: string): Observable<Cliente> {
    return this.http.get<Cliente>(this.api.buildUrl(`${this.endpoint}/autocompletar/dni/${dni}`));
  }
}
