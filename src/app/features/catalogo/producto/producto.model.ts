export interface Producto {
  id: number;
  nombre: string;
  descripcion: string;
  sector?: 'CLIMATIZACION' | 'REFRIGERACION' | 'VENTILACION_FORZADA' | 'MATERIAL_SERVICIO';
  costoBase: number;
  margenPorcentaje: number;
  moneda?: 'SOLES' | 'DOLARES';
  stockActual?: number | null;
}

/** Precio de venta sin IGV: misma fórmula que Producto.calcularPrecioVenta() del backend. */
export function precioVenta(p: Producto): number {
  return Number(p.costoBase) * (1 + Number(p.margenPorcentaje) / 100);
}
