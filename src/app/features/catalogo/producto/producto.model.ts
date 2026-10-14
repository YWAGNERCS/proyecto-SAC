export interface Producto {
  id: number;
  nombre: string;
  descripcion: string;
  /** Categoría del catálogo (relación Categoría–Producto); null en productos antiguos sin asignar. */
  categoriaId?: number | null;
  categoriaNombre?: string | null;
  sector?: 'CLIMATIZACION' | 'REFRIGERACION' | 'VENTILACION_FORZADA' | 'MATERIAL_SERVICIO';
  costoBase: number;
  margenPorcentaje: number;
  /** Precio sin IGV calculado por el backend. */
  precioVenta?: number;
  moneda?: 'SOLES' | 'DOLARES';
  stockActual?: number | null;
  stockMinimo?: number | null;
  visibleEnCatalogo?: boolean;
}

/** Precio de venta sin IGV: misma fórmula que Producto.calcularPrecioVenta() del backend. */
export function precioVenta(p: Producto): number {
  return Number(p.costoBase) * (1 + Number(p.margenPorcentaje) / 100);
}
