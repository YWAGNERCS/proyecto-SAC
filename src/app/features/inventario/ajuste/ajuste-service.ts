import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { ApiService } from '../../../core/services/api-service';

export interface AjusteInventario {
  id: number;
  productoId: number;
  producto: string;
  fecha: string;
  tipo: 'ENTRADA' | 'SALIDA';
  cantidad: number;
  motivo: string;
  motivoNombre: string;
  observacion: string | null;
  stockAnterior: number;
  stockNuevo: number;
  registradoPor: string | null;
  fechaRegistro: string;
}

export interface AjusteRequest {
  productoId: number;
  motivo: string;
  fecha: string;
  /** Conteo físico: unidades contadas (el sistema calcula la diferencia). */
  stockContado: number | null;
  tipo: 'ENTRADA' | 'SALIDA' | null;
  cantidad: number | null;
  observacion: string | null;
}

/** Ajustes de inventario: conteo físico, mermas, dañados, stock inicial... */
@Injectable({ providedIn: 'root' })
export class AjusteService {
  private api = inject(ApiService);
  private http = inject(HttpClient);
  private base = '/v1/admin/inventario/ajustes';

  buscar(filtro: { productoId?: number | null; motivo?: string; desde?: string; hasta?: string }) {
    let params = new HttpParams();
    if (filtro.productoId) params = params.set('productoId', filtro.productoId);
    if (filtro.motivo) params = params.set('motivo', filtro.motivo);
    if (filtro.desde) params = params.set('desde', filtro.desde);
    if (filtro.hasta) params = params.set('hasta', filtro.hasta);
    return this.http.get<AjusteInventario[]>(this.api.buildUrl(this.base), { params });
  }

  motivos() {
    return this.http.get<{ codigo: string; nombre: string }[]>(this.api.buildUrl(`${this.base}/motivos`));
  }

  registrar(ajuste: AjusteRequest) {
    return this.http.post<AjusteInventario>(this.api.buildUrl(this.base), ajuste);
  }
}
