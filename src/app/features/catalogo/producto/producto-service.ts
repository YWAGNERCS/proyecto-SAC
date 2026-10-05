import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Producto } from './producto.model';
import { environment } from '../../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class ProductoService {
  private http = inject(HttpClient);
  private url = `${environment.apiBaseUrl}/admin/productos`;

  listarTodos(): Observable<Producto[]> {
    return this.http.get<Producto[]>(this.url);
  }
}
