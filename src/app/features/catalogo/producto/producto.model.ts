export interface Producto {
  id: number;
  nombre: string;
  descripcion: string;
  /** Categoría del catálogo (relación Categoría–Producto); null en productos antiguos sin asignar. */
  categoriaId?: number | null;
  categoriaNombre?: string | null;
  sector?: Sector;
  costoBase: number;
  margenPorcentaje: number;
  /** Precio sin IGV calculado por el backend. */
  precioVenta?: number;
  moneda?: Moneda;
  stockActual?: number | null;
  stockMinimo?: number | null;
  visibleEnCatalogo?: boolean;
}

export type Sector = 'CLIMATIZACION' | 'REFRIGERACION' | 'VENTILACION_FORZADA' | 'MATERIAL_SERVICIO';
export type Moneda = 'SOLES' | 'DOLARES';

/** Lo que se envía al crear o editar (refleja ProductoRequestDTO del backend). */
export interface ProductoRequest {
  nombre: string;
  descripcion: string;
  categoriaId: number;
  sector: Sector;
  costoBase: number;
  margenPorcentaje: number;
  moneda: Moneda;
  stockActual: number | null;
  stockMinimo: number | null;
  visibleEnCatalogo: boolean;
}

export const SECTORES: { valor: Sector; etiqueta: string }[] = [
  { valor: 'CLIMATIZACION', etiqueta: 'Climatización' },
  { valor: 'REFRIGERACION', etiqueta: 'Refrigeración' },
  { valor: 'VENTILACION_FORZADA', etiqueta: 'Ventilación forzada' },
  { valor: 'MATERIAL_SERVICIO', etiqueta: 'Material / servicio' },
];

/** Solo los materiales controlan stock; los equipos no se almacenan. */
export function controlaStock(sector: Sector | undefined | null): boolean {
  return sector === 'MATERIAL_SERVICIO';
}

/** Precio de venta sin IGV: misma fórmula que Producto.calcularPrecioVenta() del backend. */
export function precioVenta(p: Producto): number {
  return Number(p.costoBase) * (1 + Number(p.margenPorcentaje) / 100);
}
