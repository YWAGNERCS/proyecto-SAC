export interface Proveedor {
  id?: number;
  tipo: 'PERSONA_NATURAL' | 'EMPRESA';
  nombreORazonSocial: string;
  ruc?: string;
  dni?: string;
  direccion?: string;
  telefono?: string;
  correo?: string;
  contactoVendedor?: string;
}
