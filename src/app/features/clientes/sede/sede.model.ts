/** Sede (local) de un cliente: refleja SedeDTO.Response del backend. */
export interface Sede {
  id: number;
  clienteId: number;
  nombre: string;
  direccion: string | null;
  contacto: string | null;
  telefono: string | null;
}

export interface SedeRequest {
  nombre: string;
  direccion: string | null;
  contacto: string | null;
  telefono: string | null;
}
