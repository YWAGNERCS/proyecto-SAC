/** Refleja la entidad Cliente del backend (antes había un campo nroDocumento que el backend no envía). */
export interface Cliente {
  id?: number;
  tipo: 'PERSONA_NATURAL' | 'EMPRESA';
  nombreORazonSocial: string;
  ruc?: string | null;
  dni?: string | null;
  direccion?: string | null;
  telefono?: string | null;
  correo?: string | null;
}
