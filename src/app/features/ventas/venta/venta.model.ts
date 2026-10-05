export interface DetalleVentaResponse {
  productoNombre: string;
  cantidad: number;
  precioUnitario: number;
  subtotalDetalle: number;
}

export interface VentaResponse {
  id: number;
  clienteNombre: string;
  cotizacionOrigenId: number | null;
  fechaVenta: string;
  tipoPago: 'CONTADO' | 'CREDITO';
  estadoPago: 'PENDIENTE' | 'PAGADO' | 'ATRASADO';
  fechaLimitePago: string | null;
  moneda: 'SOLES' | 'DOLARES';
  subtotal: number;
  igv: number;
  total: number;
  detalles: DetalleVentaResponse[];
}

export interface ItemVentaDTO {
  productoId: number;
  cantidad: number;
}

export interface VentaDirectaRequest {
  clienteId: number;
  items: ItemVentaDTO[];
  tipoPago: 'CONTADO' | 'CREDITO';
  diasCredito?: number;
}
