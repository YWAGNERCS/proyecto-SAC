/** Contrato de mantenimiento: refleja ContratoDTO.Response del backend. */
export interface Contrato {
  id: number;
  clienteId: number;
  clienteNombre: string;
  sedeId: number | null;
  sedeNombre: string | null;
  fechaInicio: string;
  fechaFin: string;
  frecuenciaMeses: number;
  montoVisita: number;
  moneda: 'SOLES' | 'DOLARES';
  descripcion: string;
  estado: 'VIGENTE' | 'CANCELADO';
  registradoPor: string | null;
  motivoCancelacion: string | null;
  visitasPendientes: number;
  visitasRealizadas: number;
}

export interface ContratoRequest {
  clienteId: number;
  sedeId: number | null;
  fechaInicio: string;
  fechaFin: string;
  frecuenciaMeses: number;
  montoVisita: number;
  moneda: 'SOLES' | 'DOLARES';
  descripcion: string;
}
