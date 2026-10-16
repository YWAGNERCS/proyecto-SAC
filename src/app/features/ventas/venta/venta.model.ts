export interface DetalleVentaResponse {
  productoNombre: string;
  cantidad: number;
  precioUnitario: number;
  subtotalDetalle: number;
}

export type EstadoVenta = 'REGISTRADA' | 'ANULADA';

export interface VentaResponse {
  id: number;
  clienteNombre: string;
  proformaOrigenId: number | null;
  fechaVenta: string;
  tipoPago: 'CONTADO' | 'CREDITO';
  estado: EstadoVenta;
  estadoPago: 'PENDIENTE' | 'PAGADO' | 'ATRASADO';
  fechaLimitePago: string | null;
  moneda: 'SOLES' | 'DOLARES';
  subtotal: number;
  igv: number;
  total: number;
  /** null si la venta aún no tiene factura/boleta emitida. */
  comprobanteId: number | null;
  detalles: DetalleVentaResponse[];
}

/** Filtros opcionales del reporte (GET /ventas). Fechas en formato yyyy-MM-dd. */
export interface FiltroVentas {
  estado?: EstadoVenta | '';
  desde?: string;
  hasta?: string;
}

export interface ItemVentaDTO {
  productoId: number;
  cantidad: number;
}

/** Lo único que viaja al backend: subtotales, IGV y total los calcula el servidor. */
export interface VentaDirectaRequest {
  clienteId: number;
  items: ItemVentaDTO[];
  tipoPago: 'CONTADO' | 'CREDITO';
  diasCredito?: number;
}
