import { Equipo } from '../../postventa/equipo/equipo.model';

export type TipoOrden = 'INSTALACION' | 'MANTENIMIENTO' | 'REPARACION' | 'VISITA_TECNICA';
export type EstadoOrden = 'PROGRAMADA' | 'REALIZADA' | 'CANCELADA';

export const TIPOS_ORDEN: { valor: TipoOrden; etiqueta: string }[] = [
  { valor: 'INSTALACION', etiqueta: 'Instalación' },
  { valor: 'MANTENIMIENTO', etiqueta: 'Mantenimiento' },
  { valor: 'REPARACION', etiqueta: 'Reparación' },
  { valor: 'VISITA_TECNICA', etiqueta: 'Visita técnica' },
];

export function etiquetaTipo(tipo: string): string {
  return TIPOS_ORDEN.find((t) => t.valor === tipo)?.etiqueta ?? tipo;
}

/** Orden de trabajo: refleja OrdenDTO.Response del backend. */
export interface OrdenTrabajo {
  id: number;
  tipo: TipoOrden;
  estado: EstadoOrden;
  clienteId: number;
  clienteNombre: string;
  clienteTelefono: string | null;
  sedeId: number | null;
  sedeNombre: string | null;
  ventaId: number | null;
  mantenimientoId: number | null;
  equipoId: number | null;
  equipoNombre: string | null;
  fechaProgramada: string;
  /** "HH:mm" o null (en el día). */
  horaInicio: string | null;
  duracionMinutos: number;
  tecnico: string;
  direccion: string | null;
  descripcion: string;
  fechaRealizada: string | null;
  trabajoRealizado: string | null;
  recibidoPor: string | null;
  observaciones: string | null;
  materiales: { productoId: number; productoNombre: string; cantidad: number; costoUnitario: number; moneda: string }[];
  equiposInstalados: Equipo[];
  ventaFacturadaId: number | null;
  registradoPor: string | null;
  cerradoPor: string | null;
  motivoCancelacion: string | null;
}

export interface OrdenRequest {
  tipo: TipoOrden;
  clienteId: number;
  sedeId: number | null;
  ventaId: number | null;
  mantenimientoId: number | null;
  equipoId: number | null;
  fechaProgramada: string;
  horaInicio: string | null;
  duracionMinutos: number | null;
  tecnico: string;
  direccion: string | null;
  descripcion: string;
}

export interface CierreRequest {
  fechaRealizada: string | null;
  trabajoRealizado: string;
  recibidoPor: string;
  observaciones: string | null;
  materiales: { productoId: number; cantidad: number }[];
  equipos: { productoId: number; numeroSerie: string | null; ubicacion: string | null; mesesGarantia: number | null; frecuenciaMeses: number | null }[];
}

export interface FacturacionRequest {
  items: { productoId: number; cantidad: number; descuentoPorcentaje: number | null }[];
  tipoPago: 'CONTADO' | 'CREDITO';
  diasCredito: number | null;
}
