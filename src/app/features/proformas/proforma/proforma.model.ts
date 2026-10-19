import { Cliente } from '../../clientes/cliente/cliente.model';

export interface ProformaItemDTO {
  productoId: number;
  cantidad: number;
  /** % negociado de la línea (0 a tope del usuario). */
  descuentoPorcentaje?: number;
}

// Modelo que devuelve el Backend (para la Lista)
export interface ProformaResponse {
  id: number;
  clienteNombre?: string;
  cliente?: Cliente;
  moneda: string;
  /** Soles por dólar fijado en la proforma (la venta lo hereda). */
  tipoCambio?: number | null;
  diasVigencia?: number;
  estado: string;
  subtotal?: number;
  igv?: number;
  total: number;
  fechaEmision: string;
  /** Venta generada al convertirla (estado CONVERTIDA). */
  ventaId?: number | null;
  enviadoCorreo?: boolean;
  enviadoWhatsApp?: boolean;
  items?: { productoNombre: string; cantidad: number; precioUnitario: number; descuentoPorcentaje: number; subtotalItem: number }[];
}

/** Resultado de "Enviar": SIMULADO = WhatsApp aún no se envía de verdad (solo queda registrado). */
export interface ResultadoEnvio {
  whatsapp: 'SIMULADO' | 'SIN_TELEFONO' | 'NO_SOLICITADO';
  correo: 'ENVIADO' | 'FALLO' | 'SIN_CORREO' | 'NO_SOLICITADO';
  mensaje: string;
}

// Modelo que recibe el Backend (para Crear/Editar)
export interface ProformaRequest {
  clienteId: number | null;
  moneda: string;
  diasVigencia: number;
  items: ProformaItemDTO[];
  /** Soles por dólar para convertir productos en otra moneda (queda fijo en la proforma). */
  tipoCambio?: number | null;
}