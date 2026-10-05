import { Cliente } from '../../clientes/cliente/cliente.model';

export interface CotizacionItemDTO {
  productoId: number;
  cantidad: number;
}

// Modelo que devuelve el Backend (para la Lista)
export interface CotizacionResponse {
  id: number;
  clienteNombre?: string;
  cliente?: Cliente;
  moneda: string;
  diasVigencia?: number;
  estado: string;
  subtotal?: number;
  igv?: number;
  total: number;
  fechaEmision: string;
}

// Modelo que recibe el Backend (para Crear/Editar)
export interface CotizacionRequest {
  clienteId: number | null;
  moneda: string;
  diasVigencia: number;
  items: CotizacionItemDTO[];
}