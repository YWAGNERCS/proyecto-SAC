export type EstadoMantenimiento = 'PENDIENTE' | 'REALIZADO' | 'CANCELADO' | 'VENCIDO';

/** Pestañas de la agenda (parámetro `vista` del backend). */
export type VistaPostventa = 'VENCIDOS' | 'PROXIMOS' | 'REALIZADOS' | 'TODOS';

export interface Mantenimiento {
  id: number;
  ventaId: number;
  fechaVenta: string;
  clienteId: number;
  clienteNombre: string;
  clienteTelefono: string | null;
  clienteCorreo: string | null;
  clienteDireccion: string | null;
  /** Equipos de climatización/refrigeración de la venta, p. ej. "2 × Split 12 000 BTU". */
  equipos: string;
  fechaProgramada: string;
  /** Días que faltan para la fecha programada (negativo = ya pasó). */
  diasParaFecha: number;
  estado: EstadoMantenimiento;
  fechaRealizada: string | null;
  notas: string | null;
}

export interface ResumenPostventa {
  vencidos: number;
  proximos7: number;
  proximos30: number;
}
