import { Injectable, inject } from '@angular/core';
import { ApiService } from '../../../core/services/api-service';
import { HttpClient } from '@angular/common/http';
import { CompraRequest, CompraResponse } from './compra.model';

@Injectable({ providedIn: 'root' })
export class CompraService {
  private api = inject(ApiService);
  private http = inject(HttpClient);
  private endpoint = '/admin/compras';

  listar() {
    return this.http.get<CompraResponse[]>(this.api.buildUrl(this.endpoint));
  }

  buscarPorId(id: number) {
    return this.http.get<CompraResponse>(this.api.buildUrl(`${this.endpoint}/${id}`));
  }

  registrar(data: CompraRequest) {
    return this.http.post<CompraResponse>(this.api.buildUrl(this.endpoint), data);
  }
}
