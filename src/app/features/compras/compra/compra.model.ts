export interface DetalleCompraRequest {
  productoId: number;
  cantidad: number;
  precioUnitarioCompra: number;
}

export interface CompraRequest {
  proveedorId: number;
  fechaCompra: string;
  numeroFactura?: string;
  moneda: 'SOLES' | 'DOLARES';
  total: number;
  detalles: DetalleCompraRequest[];
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
}
