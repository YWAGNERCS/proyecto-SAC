/** Una página de un listado, tal como la devuelve el backend (Pagina.java). */
export interface Pagina<T> {
  contenido: T[];
  /** Número de página, desde 0. */
  pagina: number;
  tamano: number;
  /** Registros que cumplen el filtro, en todas las páginas. */
  total: number;
  totalPaginas: number;
}

/** Arma los parámetros de página para HttpParams. */
export function paramsPagina(pagina: number, tamano: number): Record<string, number> {
  return { pagina, tamano };
}
