import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { ApiService } from './api-service';

/** Estado ante SUNAT: PENDIENTE se reintenta solo; OBSERVADO es válido con observaciones. */
export type EstadoSunat = 'PENDIENTE' | 'ACEPTADO' | 'OBSERVADO' | 'RECHAZADO';

/** Comprobante o nota de crédito con la respuesta de SUNAT a su último envío. */
export interface DocumentoSunat {
  id: number;
  numero: string;
  estado: EstadoSunat;
  codigo: number | null;
  mensaje: string | null;
  intentos: number;
  tieneXml: boolean;
  tieneCdr: boolean;
  /** "Aceptado" por el envío simulado: nunca llegó a SUNAT y no tiene validez tributaria. */
  simulado: boolean;
}

export interface EstadoSunatVenta {
  ventaId: number;
  comprobante: DocumentoSunat;
  notaCredito: DocumentoSunat | null;
}

/** Facturación electrónica: estado ante SUNAT de los comprobantes y reenvío manual. */
@Injectable({ providedIn: 'root' })
export class FacturacionService {
  private api = inject(ApiService);
  private http = inject(HttpClient);
  private endpoint = '/v1/admin/facturacion';

  /** Estado de los comprobantes de varias ventas (las que no tienen comprobante no vienen). */
  estados(ventaIds: number[]) {
    const params = new HttpParams().set('ventaIds', ventaIds.join(','));
    return this.http.get<EstadoSunatVenta[]>(this.api.buildUrl(`${this.endpoint}/estados`), { params });
  }

  /** Vuelve a enviar a SUNAT un comprobante rechazado (ya corregido) o pendiente. */
  reenviar(id: number, esNota: boolean) {
    const ruta = esNota ? `${this.endpoint}/notas-credito/${id}/reenviar` : `${this.endpoint}/${id}/reenviar`;
    return this.http.post<void>(this.api.buildUrl(ruta), {});
  }

  /** Ruta del XML firmado o del CDR (para DescargaService). */
  rutaArchivo(id: number, esNota: boolean, archivo: 'xml' | 'cdr'): string {
    return esNota ? `${this.endpoint}/notas-credito/${id}/${archivo}` : `${this.endpoint}/${id}/${archivo}`;
  }
}
