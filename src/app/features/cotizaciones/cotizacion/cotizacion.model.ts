import { Cliente } from '../../clientes/cliente/cliente.model';

// Modelo que devuelve el Backend (para la Lista)
export interface CotizacionResponse {
  id: number;
  cliente: Cliente;
  moneda: string;
  diasVigencia: number;
  estado: string;
  total: number;
  fechaEmision: string;
}

// Modelo que recibe el Backend (para Crear/Editar)
export interface CotizacionRequest {
  clienteId: number | null;
  moneda: string;
  diasVigencia: number;
  items: any[]; // Hardcoded for this simple CRUD demo
}
