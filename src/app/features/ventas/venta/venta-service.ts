import { Injectable, inject } from '@angular/core';
import { ApiService } from '../../../core/services/api-service';
import { HttpClient, HttpParams } from '@angular/common/http';
import { VentaResponse, VentaDirectaRequest, FiltroVentas } from './venta.model';
import { Pagina } from '../../../core/services/pagina';
import { RutinaMantenimiento } from '../../postventa/mantenimiento/rutina-mantenimiento';

/** Totales de las ventas REGISTRADAS de una moneda, sobre todo el filtro (no solo la página). */
export interface ResumenMoneda {
  moneda: 'SOLES' | 'DOLARES';
  cantidad: number;
  montoTotal: number;
  ticketPromedio: number;
}

export interface ReporteVentas {
  ventas: Pagina<VentaResponse>;
  resumen: ResumenMoneda[];
  anuladas: number;
}

@Injectable({ providedIn: 'root' })
export class VentaService {
  private api = inject(ApiService);
  private http = inject(HttpClient);
  private endpoint = '/v1/admin/ventas';

  /** Reporte de ventas con su detalle; solo se envían los filtros que tienen valor. */
  listar(filtro: FiltroVentas = {}) {
    let params = new HttpParams();
    if (filtro.estado) params = params.set('estado', filtro.estado);
    if (filtro.desde) params = params.set('desde', filtro.desde);
    if (filtro.hasta) params = params.set('hasta', filtro.hasta);
    return this.http.get<VentaResponse[]>(this.api.buildUrl(this.endpoint), { params });
  }

  /** Una página del reporte, con los totales calculados en el servidor sobre todo el filtro. */
  /** @param orden columna y dirección para el servidor, p. ej. "cliente,asc" (vacío = la más nueva primero) */
  reportePagina(filtro: FiltroVentas, pagina: number, tamano: number, orden = '') {
    let params = new HttpParams().set('pagina', pagina).set('tamano', tamano);
    if (orden) params = params.set('orden', orden);
    if (filtro.estado) params = params.set('estado', filtro.estado);
    if (filtro.desde) params = params.set('desde', filtro.desde);
    if (filtro.hasta) params = params.set('hasta', filtro.hasta);
    return this.http.get<ReporteVentas>(this.api.buildUrl(`${this.endpoint}/pagina`), { params });
  }

  /** @param rutina "Implementar rutinas de mantenimiento" (null = la venta no programa visitas) */
  /** fechaVenta: fecha real de la venta (AAAA-MM-DD) o null para hoy (auditoría M3). */
  convertirDesdeProforma(proformaId: number, tipoPago: string, diasCredito?: number, rutina: RutinaMantenimiento | null = null,
                         fechaVenta: string | null = null) {
    let url = `${this.api.buildUrl(this.endpoint)}/desde-proforma/${proformaId}?tipoPago=${tipoPago}`;
    if (diasCredito) {
      url += `&diasCredito=${diasCredito}`;
    }
    return this.http.post<VentaResponse>(url, { rutinaMantenimiento: rutina, fechaVenta });
  }

  registrarVentaDirecta(request: VentaDirectaRequest) {
    return this.http.post<VentaResponse>(this.api.buildUrl(`${this.endpoint}/directa`), request);
  }

  listarPagosAtrasados() {
    return this.http.get<VentaResponse[]>(this.api.buildUrl(`${this.endpoint}/pagos-atrasados`));
  }

  /** REGISTRADA -> ANULADA. Body vacío: el backend ya sabe qué stock devolver. */
  /** Motivo obligatorio (5 a 300 caracteres); la venta guarda quién la anuló (auditoría M4). */
  anular(id: number, motivo: string) {
    return this.http.patch<VentaResponse>(this.api.buildUrl(`${this.endpoint}/${id}/anular`), { motivo });
  }
}
