export type MonedaCobro = 'SOLES' | 'DOLARES';
export type MetodoPago = 'EFECTIVO' | 'TRANSFERENCIA' | 'DEPOSITO' | 'YAPE_PLIN' | 'CHEQUE' | 'DETRACCION' | 'RETENCION';

export const METODOS_PAGO: { valor: MetodoPago; etiqueta: string }[] = [
  { valor: 'TRANSFERENCIA', etiqueta: 'Transferencia' },
  { valor: 'DEPOSITO', etiqueta: 'Depósito' },
  { valor: 'EFECTIVO', etiqueta: 'Efectivo' },
  { valor: 'YAPE_PLIN', etiqueta: 'Yape / Plin' },
  { valor: 'CHEQUE', etiqueta: 'Cheque' },
  { valor: 'DETRACCION', etiqueta: 'Constancia de detracción' },
];

/** Venta a crédito con saldo pendiente. */
export interface CuentaPorCobrar {
  ventaId: number;
  comprobante: string | null;
  clienteId: number;
  clienteNombre: string;
  fechaVenta: string;
  fechaLimitePago: string | null;
  /** > 0 si ya pasó la fecha límite. */
  diasAtraso: number;
  moneda: MonedaCobro;
  total: number;
  cobrado: number;
  saldo: number;
  tipoDeduccion: 'NINGUNA' | 'DETRACCION' | 'RETENCION';
  montoDeduccion: number | null;
  /** Detracción o retención que falta acreditar con su constancia o comprobante. */
  deduccionPendiente: number;
  /** Lo que el cliente todavía debe pagarle directamente a JD. */
  netoPorCobrar: number;
  montoDetraccionSoles?: number | null;
  tipoCambio?: number | null;
}

export interface CobroRequest {
  clienteId: number;
  fecha: string | null;
  moneda: MonedaCobro;
  metodoPago: MetodoPago;
  referencia: string | null;
  notas: string | null;
  tipoCambioRetencion?: number | null;
  abonos: { ventaId: number; monto: number; baseRetencion?: number | null }[];
}

export interface Cobro {
  id: number;
  clienteId: number;
  clienteNombre: string;
  fecha: string;
  moneda: MonedaCobro;
  metodoPago: MetodoPago;
  referencia: string | null;
  notas: string | null;
  montoTotal: number;
  tipoCambioRetencion?: number | null;
  abonos: { ventaId: number; monto: number; baseRetencion?: number | null }[];
  /** VIGENTE cuenta en el saldo; ANULADO queda solo como registro (con fecha, motivo y usuario). */
  estado: 'VIGENTE' | 'ANULADO';
  registradoPor: string | null;
  fechaAnulacion: string | null;
  motivoAnulacion: string | null;
  anuladoPor: string | null;
}

/** Lo que el cliente pagó de una venta (al contado o con abonos): se le devuelve si se anula. */
export interface PagadoDeVenta {
  ventaId: number;
  moneda: MonedaCobro;
  pagado: number;
}

export type EstadoDevolucion = 'PENDIENTE' | 'DEVUELTA';

/** Dinero por devolver de una venta anulada que el cliente ya había pagado. */
export interface Devolucion {
  id: number;
  ventaId: number;
  clienteId: number;
  clienteNombre: string;
  moneda: MonedaCobro;
  monto: number;
  estado: EstadoDevolucion;
  fechaAnulacion: string;
  fechaDevolucion: string | null;
  metodoPago: MetodoPago | null;
  referencia: string | null;
  notas: string | null;
}

export interface DevolucionRequest {
  fecha: string | null;
  metodoPago: MetodoPago;
  referencia: string | null;
  notas: string | null;
}
