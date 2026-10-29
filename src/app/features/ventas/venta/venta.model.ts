export interface DetalleVentaResponse {
  productoNombre: string;
  cantidad: number;
  precioUnitario: number;
  /** % de descuento de la línea (0 si no hay). */
  descuentoPorcentaje: number;
  subtotalDetalle: number;
}

export type EstadoVenta = 'REGISTRADA' | 'ANULADA';

export interface VentaResponse {
  id: number;
  clienteId: number;
  clienteNombre: string;
  proformaOrigenId: number | null;
  fechaVenta: string;
  tipoPago: 'CONTADO' | 'CREDITO';
  estado: EstadoVenta;
  estadoPago: 'PENDIENTE' | 'PAGADO' | 'ATRASADO';
  fechaLimitePago: string | null;
  moneda: 'SOLES' | 'DOLARES';
  /** Soles por dólar usado en la venta (null si no se registró). */
  tipoCambio: number | null;
  subtotal: number;
  igv: number;
  total: number;
  /** Detracción (SPOT) o retención del IGV calculada al registrar la venta. */
  tipoDeduccion: 'NINGUNA' | 'DETRACCION' | 'RETENCION';
  porcentajeDeduccion: number | null;
  montoDeduccion: number | null;
  /** Lo que el cliente le paga directamente a JD (total − deducción). */
  netoACobrar: number;
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
  /** % negociado de la línea (0 a tope del usuario). */
  descuentoPorcentaje?: number;
}

/** Lo único que viaja al backend: subtotales, IGV y total los calcula el servidor. */
export interface VentaDirectaRequest {
  clienteId: number;
  items: ItemVentaDTO[];
  tipoPago: 'CONTADO' | 'CREDITO';
  diasCredito?: number;
  /** Moneda en la que se cobra; los precios se convierten con el tipo de cambio. */
  moneda: 'SOLES' | 'DOLARES';
  /** Soles por dólar (el de SUNAT del día o el negociado). */
  tipoCambio?: number | null;
}
