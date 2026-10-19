export type MonedaCobro = 'SOLES' | 'DOLARES';
export type MetodoPago = 'EFECTIVO' | 'TRANSFERENCIA' | 'DEPOSITO' | 'YAPE_PLIN' | 'CHEQUE';

export const METODOS_PAGO: { valor: MetodoPago; etiqueta: string }[] = [
  { valor: 'TRANSFERENCIA', etiqueta: 'Transferencia' },
  { valor: 'DEPOSITO', etiqueta: 'Depósito' },
  { valor: 'EFECTIVO', etiqueta: 'Efectivo' },
  { valor: 'YAPE_PLIN', etiqueta: 'Yape / Plin' },
  { valor: 'CHEQUE', etiqueta: 'Cheque' },
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
}

export interface CobroRequest {
  clienteId: number;
  fecha: string | null;
  moneda: MonedaCobro;
  metodoPago: MetodoPago;
  referencia: string | null;
  notas: string | null;
  abonos: { ventaId: number; monto: number }[];
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
  abonos: { ventaId: number; monto: number }[];
}
