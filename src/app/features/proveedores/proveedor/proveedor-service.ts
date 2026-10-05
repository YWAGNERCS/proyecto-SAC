import { Injectable, inject } from '@angular/core';
import { ApiService } from '../../../core/services/api-service';
import { HttpClient } from '@angular/common/http';
import { Proveedor } from './proveedor.model';

@Injectable({ providedIn: 'root' })
export class ProveedorService {
  private api = inject(ApiService);
  private http = inject(HttpClient);
  private endpoint = '/admin/proveedores';

  listarTodos() {
    return this.http.get<Proveedor[]>(this.api.buildUrl(this.endpoint));
  }
}
