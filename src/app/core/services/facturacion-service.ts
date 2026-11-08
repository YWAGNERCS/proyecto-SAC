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
  /** Ventas sin comprobante todavía porque falta un dato de JD (cuenta de detracciones). */
  porEmitir: number;
  ventasPorEmitir: number[];
  /** SUNAT rechazó el usuario o la clave SOL: no se envía nada hasta corregirlos y reiniciar. null si todo va bien. */
  envioDetenido: string | null;
}

export interface EstadoSunatVenta {
  ventaId: number;
  /** null mientras la venta está por emitir (ver porEmitir). */
  comprobante: DocumentoSunat | null;
  notaCredito: DocumentoSunat | null;
  /** Comprobantes anteriores de la venta que SUNAT rechazó y se reemplazaron (historial de emisiones). */
  reemplazados: DocumentoSunat[];
  /**
   * La venta todavía no tiene comprobante porque falta un dato de JD (la cuenta de detracciones): el motivo.
   * No usó número ni corre su plazo; se emite solo cuando el dato esté. null si ya tiene comprobante.
   */
  porEmitir: string | null;
  /** Entrega del comprobante vigente por correo al cliente (null si todavía no tiene comprobante). */
  correo?: EntregaCorreo | null;
}

/**
 * Estado del correo del comprobante. ENVIADO = el servidor de correo (SMTP) lo aceptó, no que el cliente lo leyó.
 * INCIERTO = se interrumpió en pleno envío y no se sabe si salió: no se reenvía solo.
 */
export type EstadoCorreo = 'PENDIENTE' | 'EN_PROCESO' | 'ENVIADO' | 'FALLIDO' | 'INCIERTO';

export interface IntentoCorreo {
  intento: number;
  inicio: string;
  fin: string | null;
  resultado: 'ENVIADO' | 'FALLIDO' | 'INCIERTO';
  mensaje: string | null;
  messageId: string | null;
  /** Nombre, tamaño y huella SHA-256 de cada archivo adjunto. */
  adjuntos: string | null;
}

/** Una solicitud de envío por correo (la automática al aceptarlo SUNAT, o un reenvío). */
export interface EnvioCorreo {
  id: number;
  destinatario: string;
  tipo: 'AUTOMATICO' | 'MANUAL';
  estado: EstadoCorreo;
  solicitadoPor: string | null;
  fechaCreacion: string;
  fechaUltimoIntento: string | null;
  fechaAceptacionSmtp: string | null;
  intentos: number;
  ultimoError: string | null;
  messageId: string | null;
  /** FALLIDO: cuándo se vuelve a intentar solo (null = ya no se reintenta solo). */
  proximoIntento: string | null;
  intentosDetalle: IntentoCorreo[];
}

export interface EntregaCorreo {
  comprobanteId: number;
  /** Correo registrado del cliente hoy. */
  correoCliente: string | null;
  /** Por qué no se envía (todavía no válido, beta de SUNAT, cliente sin correo…); null si se puede. */
  motivoNoSeEnvia: string | null;
  ultimo: EnvioCorreo | null;
  envios: number;
  ultimaAceptacionSmtp: string | null;
  /** Todos los envíos con sus intentos (solo en el detalle). */
  historial: EnvioCorreo[];
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

  /** Entrega por correo de un comprobante, con cada envío e intento. */
  entregaCorreo(id: number) {
    return this.http.get<EntregaCorreo>(this.api.buildUrl(`${this.endpoint}/${id}/correo`));
  }

  /**
   * Envía (o reenvía) el comprobante por correo al correo registrado del cliente. confirmar = true cuando ya se había
   * enviado: sin eso, el servidor responde 409.
   */
  reenviarCorreo(id: number, confirmar: boolean) {
    const params = new HttpParams().set('confirmar', confirmar);
    return this.http.post<EntregaCorreo>(this.api.buildUrl(`${this.endpoint}/${id}/enviar`), {}, { params });
  }

  /** Ruta del PDF del comprobante (el mismo que se adjunta al correo), para DescargaService. */
  rutaPdf(id: number): string {
    return `${this.endpoint}/${id}/pdf`;
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
