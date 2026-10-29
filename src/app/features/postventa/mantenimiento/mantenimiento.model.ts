export type EstadoMantenimiento = 'PENDIENTE' | 'REALIZADO' | 'CANCELADO' | 'VENCIDO';

/** Pestañas de la agenda (parámetro `vista` del backend). */
export type VistaPostventa = 'VENCIDOS' | 'PROXIMOS' | 'REALIZADOS' | 'TODOS';

export interface Mantenimiento {
  id: number;
  /** De dónde salió la visita: las 4 de una venta, el plan de un equipo instalado o un contrato. */
  origen: 'VENTA' | 'EQUIPO' | 'CONTRATO';
  ventaId: number | null;
  fechaVenta: string | null;
  equipoId: number | null;
  contratoId: number | null;
  clienteId: number;
  clienteNombre: string;
  clienteTelefono: string | null;
  clienteCorreo: string | null;
  clienteDireccion: string | null;
  sedeNombre: string | null;
  /** Equipos de la venta ("2 × Split 12 000 BTU"), el equipo con su serie o el contrato. */
  equipos: string;
  fechaProgramada: string;
  /** Días que faltan para la fecha programada (negativo = ya pasó). */
  diasParaFecha: number;
  estado: EstadoMantenimiento;
  fechaRealizada: string | null;
  tecnico: string | null;
  ordenTrabajoId: number | null;
  notas: string | null;
}

export interface ResumenPostventa {
  vencidos: number;
  proximos7: number;
  proximos30: number;
}
