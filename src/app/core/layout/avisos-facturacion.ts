import { PlazoEnvio } from '../services/facturacion-service';

/** Un aviso de la campana: qué pasa y a dónde ir para resolverlo. */
export interface Alerta {
  nivel: 'alta' | 'media' | 'info';
  titulo: string;
  detalle: string;
  ruta: string;
  query?: Record<string, string>;
}

/** "1 comprobante" / "3 comprobantes". */
export function plural(n: number, uno: string, varios: string): string {
  return `${n} ${n === 1 ? uno : varios}`;
}

/** Los tres primeros de una lista, con "…" si hay más. */
export function primeros(valores: string[]): string {
  return valores.slice(0, 3).join(', ') + (valores.length > 3 ? '…' : '');
}

/**
 * Avisos de facturación electrónica para la campana, a partir del resumen del plazo de envío
 * (GET /facturacion/plazo-envio). Función pura, probada en avisos-facturacion.spec.ts. Sin resumen
 * (Facturación no respondió o el usuario no tiene acceso), no hay avisos.
 */
export function avisosDeFacturacion(plazo: PlazoEnvio | null): Alerta[] {
  if (!plazo) return [];
  const lista: Alerta[] = [];
  // Plazo de 3 días para que SUNAT reciba facturas y boletas: lo enviado tarde no tiene validez
  if (plazo.fueraDePlazo > 0) {
    lista.push({ nivel: 'alta', titulo: plural(plazo.fueraDePlazo, 'comprobante fuera de plazo', 'comprobantes fuera de plazo'),
      detalle: `No llegaron a SUNAT a tiempo: emita un comprobante de reemplazo (${primeros(plazo.numerosFueraDePlazo)})`, ruta: '/ventas' });
  }
  // Usuario o clave SOL rechazados: no sale nada a SUNAT hasta corregirlos y reiniciar
  if (plazo.envioDetenido) {
    lista.push({ nivel: 'alta', titulo: 'Envíos a SUNAT detenidos',
      detalle: 'SUNAT rechazó el usuario o la clave SOL: corríjalos y reinicie el sistema', ruta: '/ventas' });
  }
  // Ventas con detracción esperando la cuenta de detracciones: no tienen comprobante todavía
  if (plazo.porEmitir > 0) {
    lista.push({ nivel: 'alta', titulo: plural(plazo.porEmitir, 'venta sin comprobante', 'ventas sin comprobante'),
      detalle: `Falta configurar la cuenta de detracciones para emitirlo (venta ${primeros(plazo.ventasPorEmitir.map((id) => '#' + id))})`,
      ruta: '/ventas' });
  }
  if (plazo.porVencer > 0) {
    lista.push({ nivel: 'alta', titulo: plural(plazo.porVencer, 'comprobante sin enviar a SUNAT', 'comprobantes sin enviar a SUNAT'),
      detalle: `El plazo vence hoy o mañana (${primeros(plazo.numerosPorVencer)})`, ruta: '/ventas' });
  }
  return lista;
}
