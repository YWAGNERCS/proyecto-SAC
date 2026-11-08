import { DocumentoSunat, EnvioSunat, EstadoSunatVenta } from '../../../core/services/facturacion-service';

/**
 * Reglas de pantalla del comprobante electrónico en Ventas: qué etiqueta lleva cada documento, qué botón se
 * ofrece y qué avisos se muestran. Funciones puras (sin HTTP ni estado) para poder probarlas solas
 * (sunat-documento.spec.ts); VentaListComponent las usa tal cual.
 */

/** Línea de la sección "Comprobante electrónico" de una venta. */
export interface LineaSunat {
  d: DocumentoSunat;
  esNota: boolean;
  titulo: string;
  reemplazado: boolean;
}

/**
 * SUNAT respondió 1033: ya tenía ese número por un envío anterior y no dice si fue aceptado o rechazado.
 * No tiene validez hasta verificarlo en SUNAT, y reenviarlo solo repetiría la misma respuesta.
 */
export function porConfirmar(d: DocumentoSunat): boolean {
  return d.estado === 'PENDIENTE' && d.codigo === 1033;
}

export function etiquetaSunat(d: DocumentoSunat, reemplazado = false): string {
  if (d.simulado) return 'Simulado (sin envío a SUNAT)';
  // Reemplazado sin rechazo de SUNAT: pasó su plazo sin llegar a SUNAT
  if (reemplazado && !d.rechazoDefinitivo && (d.estado === 'PENDIENTE' || d.estado === 'RECHAZADO')) return 'No llegó a SUNAT a tiempo';
  if (porConfirmar(d)) return 'Por confirmar en SUNAT';
  if (d.fueraDePlazo) return 'Fuera de plazo: no es válido';
  switch (d.estado) {
    case 'ACEPTADO': return 'Aceptado por SUNAT';
    case 'OBSERVADO': return 'Aceptado con observaciones';
    case 'RECHAZADO': return d.rechazoDefinitivo ? 'Rechazado por SUNAT: no es válido' : 'SUNAT no lo recibió: corrija y reenvíe';
    default: return 'Pendiente de envío';
  }
}

/** Etiqueta corta bajo el estado de la venta, en la fila de la tabla. */
export function etiquetaCorta(d: DocumentoSunat): string {
  if (d.simulado) return 'SUNAT: simulado';
  if (porConfirmar(d)) return 'SUNAT: por confirmar';
  if (d.fueraDePlazo) return 'SUNAT: fuera de plazo';
  if (d.estado === 'RECHAZADO' && !d.rechazoDefinitivo) return 'SUNAT: no recibido';
  return { ACEPTADO: 'SUNAT: aceptado', OBSERVADO: 'SUNAT: observado', RECHAZADO: 'SUNAT: rechazado', PENDIENTE: 'SUNAT: pendiente' }[d.estado];
}

export function claseSunat(d: DocumentoSunat): string {
  if (d.simulado) return 'badge-neutro';
  if (d.fueraDePlazo) return 'badge-danger';
  const estado = d.estado;
  return estado === 'ACEPTADO' ? 'badge-success' : estado === 'RECHAZADO' ? 'badge-danger' : 'badge-warning';
}

/**
 * Comprobante vigente y, si existe, nota de crédito de la venta, para la línea "Comprobante electrónico".
 * Después, los comprobantes anteriores que se reemplazaron (historial de emisiones), del más reciente al más antiguo.
 * Una venta "por emitir" (sin comprobante todavía) no tiene líneas: se muestra aparte.
 */
export function documentosSunat(s: EstadoSunatVenta): LineaSunat[] {
  if (!s.comprobante) return [];
  const docs: LineaSunat[] = [{ d: s.comprobante, esNota: false, titulo: 'Comprobante', reemplazado: false }];
  if (s.notaCredito) docs.push({ d: s.notaCredito, esNota: true, titulo: 'Nota de crédito', reemplazado: false });
  [...(s.reemplazados ?? [])].reverse().forEach((d) => docs.push({ d, esNota: false, titulo: 'Reemplazado', reemplazado: true }));
  return docs;
}

/**
 * Se puede reenviar con el mismo número lo que SUNAT no recibió (ya corregido) y lo pendiente que tiene un
 * aviso o agotó los reintentos. No lo que está por confirmar (1033), lo rechazado con CDR, lo que pasó el plazo
 * ni lo que necesita un comprobante de reemplazo (p. ej. el cliente cambió de tipo): ahí se ofrece el reemplazo.
 */
export function puedeReenviar(d: DocumentoSunat): boolean {
  if (porConfirmar(d) || d.rechazoDefinitivo || d.fueraDePlazo || d.puedeReemplazar) return false;
  return d.estado === 'RECHAZADO' || (d.estado === 'PENDIENTE' && d.intentos > 0);
}

/** AAAA-MM-DD a DD/MM. */
export function fechaCorta(iso: string | null): string {
  return iso ? `${iso.slice(8, 10)}/${iso.slice(5, 7)}` : '';
}

/** Fecha local (no UTC) en AAAA-MM-DD. */
export function isoLocal(fecha: Date): string {
  return `${fecha.getFullYear()}-${String(fecha.getMonth() + 1).padStart(2, '0')}-${String(fecha.getDate()).padStart(2, '0')}`;
}

/** Aviso del plazo de envío para un documento que SUNAT todavía no registró (null si no aplica). */
export function avisoPlazo(d: DocumentoSunat, hoy: Date = new Date()): string | null {
  if (!d.fechaLimiteEnvio || d.fueraDePlazo || d.simulado) return null;
  if (d.fechaLimiteEnvio === isoLocal(hoy)) return 'Vence hoy el plazo para enviarlo a SUNAT';
  return `Enviar a SUNAT a más tardar el ${fechaCorta(d.fechaLimiteEnvio)}`;
}

/** Por qué se ofrece el comprobante de reemplazo, para el cuadro de confirmación. */
export function motivoReemplazo(d: DocumentoSunat): string {
  if (d.fueraDePlazo) {
    return `${d.numero} no llegó a SUNAT dentro del plazo de 3 días (vencía el ${fechaCorta(d.fechaLimiteEnvio)}), ` +
      `así que enviarlo ahora ya no le daría validez.`;
  }
  if (!d.rechazoDefinitivo) {
    return `SUNAT no recibió ${d.numero} y el cliente cambió de tipo (empresa o persona natural): reenviado con su número ` +
      `saldría el tipo de comprobante equivocado.`;
  }
  return `SUNAT rechazó ${d.numero}${d.codigo !== null ? ` (código ${d.codigo})` : ''}, así que ese comprobante no es válido ` +
    `y su número ya no puede usarse.`;
}

/** Resultado de un envío del historial, en palabras. */
export function etiquetaResultado(e: EnvioSunat): string {
  return ({
    ACEPTADO: 'Aceptado', OBSERVADO: 'Aceptado con observaciones', RECHAZADO: 'Rechazado',
    REINTENTAR: 'Sin respuesta de SUNAT', POR_CONFIRMAR: 'Ya registrado: por confirmar',
    CREDENCIALES: 'Usuario o clave SOL rechazados',
    // Envíos copiados por migracion_comprobante_reemplazo.sql: guardan el estado que tenía el comprobante
    PENDIENTE: 'Pendiente (no se envió)',
  } as Record<string, string>)[e.resultado] ?? e.resultado;
}
