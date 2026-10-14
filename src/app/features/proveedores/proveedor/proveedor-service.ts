import { Injectable, inject } from '@angular/core';
import { ApiService } from '../../../core/services/api-service';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Proveedor } from './proveedor.model';

@Injectable({ providedIn: 'root' })
export class ProveedorService {
  private api = inject(ApiService);
  private http = inject(HttpClient);
  private endpoint = '/v1/admin/proveedores';

  listarTodos(): Observable<Proveedor[]> {
    return this.http.get<Proveedor[]>(this.api.buildUrl(this.endpoint));
  }

  obtener(id: number): Observable<Proveedor> {
    return this.http.get<Proveedor>(this.api.buildUrl(`${this.endpoint}/${id}`));
  }

  crear(proveedor: Proveedor): Observable<Proveedor> {
    return this.http.post<Proveedor>(this.api.buildUrl(this.endpoint), proveedor);
  }

  actualizar(id: number, proveedor: Proveedor): Observable<Proveedor> {
    return this.http.put<Proveedor>(this.api.buildUrl(`${this.endpoint}/${id}`), proveedor);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(this.api.buildUrl(`${this.endpoint}/${id}`));
  }

  /**
   * Trae razón social y dirección desde SUNAT (no guarda nada).
   * Si el RUC ya está registrado como proveedor, el backend devuelve ese registro (con id).
   */
  autocompletarRuc(ruc: string): Observable<Proveedor> {
    return this.http.get<Proveedor>(this.api.buildUrl(`${this.endpoint}/autocompletar/ruc/${ruc}`));
  }

  /** Trae nombres y apellidos desde RENIEC (no guarda nada). */
  autocompletarDni(dni: string): Observable<Proveedor> {
    return this.http.get<Proveedor>(this.api.buildUrl(`${this.endpoint}/autocompletar/dni/${dni}`));
  }
}
