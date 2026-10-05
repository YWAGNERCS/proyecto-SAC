import { Injectable, inject } from '@angular/core';
import { ApiService } from '../../../core/services/api-service';
import { HttpClient } from '@angular/common/http';
import { Cliente } from './cliente.model';

@Injectable({ providedIn: 'root' })
export class ClienteService {
  private api = inject(ApiService);
  private http = inject(HttpClient);
  private endpoint = '/admin/clientes';

  listarTodos() {
    return this.http.get<Cliente[]>(this.api.buildUrl(this.endpoint));
  }
}
