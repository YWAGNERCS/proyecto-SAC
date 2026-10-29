/** Equipo instalado en el local de un cliente: refleja EquipoDTO.Response del backend. */
export interface Equipo {
  id: number;
  clienteId: number;
  clienteNombre: string;
  sedeId: number | null;
  sedeNombre: string | null;
  productoId: number;
  productoNombre: string;
  sector: string;
  numeroSerie: string | null;
  ubicacion: string | null;
  fechaInstalacion: string;
  mesesGarantia: number;
  garantiaHasta: string;
  enGarantia: boolean;
  /** Cada cuántos meses se le da mantenimiento (null = sin plan propio). */
  frecuenciaMeses: number | null;
  proximoMantenimiento: string | null;
  estado: 'ACTIVO' | 'RETIRADO';
  ventaId: number | null;
  ordenTrabajoId: number | null;
  notas: string | null;
}

export interface EquipoRegistro {
  clienteId: number;
  sedeId: number | null;
  productoId: number;
  numeroSerie: string | null;
  ubicacion: string | null;
  fechaInstalacion: string;
  mesesGarantia: number | null;
  frecuenciaMeses: number | null;
  ventaId: number | null;
  notas: string | null;
}

export interface EquipoEdicion {
  sedeId: number | null;
  numeroSerie: string | null;
  ubicacion: string | null;
  mesesGarantia: number;
  frecuenciaMeses: number | null;
  notas: string | null;
}

/** Frecuencias de mantenimiento que acepta el backend. */
export const FRECUENCIAS: { valor: number | null; etiqueta: string }[] = [
  { valor: null, etiqueta: 'Sin plan propio' },
  { valor: 1, etiqueta: 'Cada mes' },
  { valor: 2, etiqueta: 'Cada 2 meses' },
  { valor: 3, etiqueta: 'Cada 3 meses' },
  { valor: 4, etiqueta: 'Cada 4 meses' },
  { valor: 6, etiqueta: 'Cada 6 meses' },
  { valor: 12, etiqueta: 'Una vez al año' },
];

export function etiquetaFrecuencia(meses: number | null): string {
  return FRECUENCIAS.find((f) => f.valor === meses)?.etiqueta ?? `Cada ${meses} meses`;
}
