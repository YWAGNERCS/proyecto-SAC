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
  /** Agente de retención del IGV designado por SUNAT: retiene el 3 % de las facturas de más de S/ 700. */
  agenteRetencion?: boolean;
}
