import { isoLocal } from './sunat-documento';

/**
 * Fecha real de una venta (auditoría M3): hasta 30 días atrás, nunca futura, solo para el administrador o quien
 * tenga el permiso "Ventas con fecha pasada". Mismas reglas que VentaService.resolverFechaVenta del backend,
 * que es quien manda: esto solo arma el campo y avisa antes de enviar. La factura sale igual con fecha de hoy.
 */
export const DIAS_FECHA_PASADA = 30;

/** Hoy, en AAAA-MM-DD (fecha local). */
export function hoyIso(hoy: Date = new Date()): string {
  return isoLocal(hoy);
}

/** La fecha más antigua que se acepta: hoy menos 30 días. */
export function fechaMinimaVenta(hoy: Date = new Date()): string {
  const d = new Date(hoy.getFullYear(), hoy.getMonth(), hoy.getDate());
  d.setDate(d.getDate() - DIAS_FECHA_PASADA);
  return isoLocal(d);
}

/** Por qué la fecha no se acepta, o null si está bien (vacía = hoy). */
export function errorFechaVenta(fecha: string | null | undefined, hoy: Date = new Date()): string | null {
  if (!fecha) return null;
  if (fecha > hoyIso(hoy)) return 'La fecha de la venta no puede ser futura.';
  if (fecha < fechaMinimaVenta(hoy)) return `La fecha de la venta puede ser de hasta ${DIAS_FECHA_PASADA} días atrás.`;
  return null;
}

/** Lo que se envía al backend: null si es hoy (o vacía), la fecha si es pasada. */
export function fechaParaEnviar(fecha: string | null | undefined, hoy: Date = new Date()): string | null {
  return fecha && fecha !== hoyIso(hoy) ? fecha : null;
}

/** AAAA-MM-DD a DD/MM/AAAA. */
export function fechaLarga(iso: string): string {
  return `${iso.slice(8, 10)}/${iso.slice(5, 7)}/${iso.slice(0, 4)}`;
}
