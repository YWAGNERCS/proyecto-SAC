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
  /** SUNAT lo rechazó con constancia (CDR): su número quedó usado y no puede reenviarse. */
  rechazoDefinitivo: boolean;
  /** Rechazo definitivo o fuera de plazo de un comprobante vigente de una venta no anulada: se puede emitir el reemplazo. */
  puedeReemplazar: boolean;
  /** Último día (AAAA-MM-DD) para que SUNAT lo reciba, si todavía no lo registró; null si ya no aplica. */
  fechaLimiteEnvio: string | null;
  /** Pasó esa fecha sin que SUNAT lo registrara: ya no se envía, se emite un reemplazo. */
  fueraDePlazo: boolean;
}

/** Facturas y boletas que SUNAT aún no registró: vencen su plazo de envío hoy o mañana, o ya lo pasaron. */
export interface PlazoEnvio {
  porVencer: number;
  fueraDePlazo: number;
  numerosPorVencer: string[];
  numerosFueraDePlazo: string[];
}

export interface EstadoSunatVenta {
  ventaId: number;
  comprobante: DocumentoSunat;
  notaCredito: DocumentoSunat | null;
  /** Comprobantes anteriores de la venta que SUNAT rechazó y se reemplazaron (historial de emisiones). */
  reemplazados: DocumentoSunat[];
}

/** Una respuesta de SUNAT del historial de envíos de un documento. */
export interface EnvioSunat {
  id: number;
  intento: number;
  fecha: string;
  /** ACEPTADO, OBSERVADO, RECHAZADO, REINTENTAR (sin respuesta) o POR_CONFIRMAR (1033). */
  resultado: string;
  codigo: number | null;
  mensaje: string | null;
  tieneXml: boolean;
  tieneCdr: boolean;
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

  /** Emite el comprobante de reemplazo de uno que SUNAT rechazó con CDR (número nuevo, misma venta). */
  reemplazar(id: number) {
    return this.http.post<{ serie: string; numero: string }>(this.api.buildUrl(`${this.endpoint}/${id}/reemplazar`), {});
  }

  /** Resumen del plazo de envío de 3 días (para la campana). */
  plazoEnvio() {
    return this.http.get<PlazoEnvio>(this.api.buildUrl(`${this.endpoint}/plazo-envio`));
  }

  /** Historial de envíos a SUNAT de un comprobante o nota de crédito, el más reciente primero. */
  envios(id: number, esNota: boolean) {
    const ruta = esNota ? `${this.endpoint}/notas-credito/${id}/envios` : `${this.endpoint}/${id}/envios`;
    return this.http.get<EnvioSunat[]>(this.api.buildUrl(ruta));
  }

  /** Ruta del XML o CDR de un envío del historial (para DescargaService). */
  rutaArchivoEnvio(envioId: number, archivo: 'xml' | 'cdr'): string {
    return `${this.endpoint}/envios/${envioId}/${archivo}`;
  }

  /** Ruta del XML firmado o del CDR (para DescargaService). */
  rutaArchivo(id: number, esNota: boolean, archivo: 'xml' | 'cdr'): string {
    return esNota ? `${this.endpoint}/notas-credito/${id}/${archivo}` : `${this.endpoint}/${id}/${archivo}`;
  }
}
