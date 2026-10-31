export interface DetalleCompraRequest {
  productoId: number;
  cantidad: number;
  precioUnitarioCompra: number;
}

export interface CompraRequest {
  proveedorId: number;
  fechaCompra: string;
  /** Obligatorio, formato SERIE-NÚMERO (F001-123). La misma factura del mismo proveedor no se acepta dos veces. */
  numeroFactura: string;
  moneda: 'SOLES' | 'DOLARES';
  /** Referencial: el backend recalcula el total con las líneas. */
  total: number;
  detalles: DetalleCompraRequest[];
  /** CONTADO (por defecto) o CREDITO: a crédito queda en Cuentas por pagar. */
  tipoPago: 'CONTADO' | 'CREDITO';
  /** Solo a crédito: 1 a 365. */
  diasCredito: number | null;
}

export interface CompraResponse {
  id: number;
  proveedor?: { id: number; nombreORazonSocial: string };
  fechaCompra: string;
  numeroFactura?: string;
  moneda: 'SOLES' | 'DOLARES';
  total: number;
  detalles: {
    id: number;
    producto: { id: number; nombre: string };
    cantidad: number;
    precioUnitarioCompra: number;
  }[];
  tipoPago: 'CONTADO' | 'CREDITO';
  diasCredito: number | null;
  fechaVencimiento: string | null;
  estadoPago: 'PAGADA' | 'PENDIENTE';
  /** Pagado al proveedor (al contado = total). */
  pagado: number | null;
  saldo: number | null;
  registradoPor: string | null;
}
