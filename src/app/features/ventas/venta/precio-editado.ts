/**
 * Precio editado de una línea de venta o proforma (auditoría M1). Mismas reglas que PrecioDeLinea del backend, que
 * es quien manda: solo puede subir (para bajarlo está el descuento), con un motivo de 5 a 200 caracteres y solo el
 * administrador o quien tenga el permiso "Editar precio en la venta". El catálogo no cambia.
 */
export const MOTIVO_PRECIO_MINIMO = 5;
export const MOTIVO_PRECIO_MAXIMO = 200;

const redondear = (n: number) => Math.round(n * 100) / 100;

/** Por qué el precio editado no se acepta, o null si está bien. */
export function errorPrecioEditado(catalogo: number, nuevo: number | null | undefined, motivo: string | null | undefined): string | null {
  if (nuevo === null || nuevo === undefined || Number.isNaN(Number(nuevo))) return 'Escriba el nuevo precio.';
  if (redondear(Number(nuevo)) <= redondear(catalogo)) {
    return 'El precio solo puede subir. Para bajarlo use el descuento de la línea.';
  }
  const m = (motivo ?? '').trim();
  if (m.length < MOTIVO_PRECIO_MINIMO || m.length > MOTIVO_PRECIO_MAXIMO) {
    return `Indique por qué cambia el precio (de ${MOTIVO_PRECIO_MINIMO} a ${MOTIVO_PRECIO_MAXIMO} caracteres).`;
  }
  return null;
}

/** Lo que guarda la línea de venta o de proforma. */
export interface PrecioDeLinea {
  precioUnitario: number;
  precioCatalogo?: number | null;
  precioEditadoPor?: string | null;
  motivoPrecio?: string | null;
}

const dinero = (n: number) => Number(n).toFixed(2);

/** "Precio de catálogo S/ 525.00, se vendió a S/ 560.00 (editado por lucy: subió el costo)", o null si no se editó. */
export function textoPrecioEditado(d: PrecioDeLinea, simbolo: string, verbo = 'se vendió a'): string | null {
  if (!d.precioEditadoPor || d.precioCatalogo === null || d.precioCatalogo === undefined) return null;
  return `Precio de catálogo ${simbolo} ${dinero(d.precioCatalogo)}, ${verbo} ${simbolo} ${dinero(d.precioUnitario)}`
    + ` (editado por ${d.precioEditadoPor}: ${d.motivoPrecio})`;
}

/** Datos del producto que necesita el aviso de la última compra. */
export interface CostoDeProducto {
  costoBase: number;
  ultimoCostoCompra?: number | null;
  fechaUltimaCompra?: string | null;
}

/**
 * Aviso para la línea si la última compra salió más cara que el costo del catálogo: el precio de catálogo quedó
 * corto y conviene usar "Editar precio". Todo en la moneda del producto y sin IGV. null = no hay nada que avisar.
 *
 * @param precioCatalogo precio de venta del catálogo (sin IGV, moneda del producto)
 */
export function avisoUltimaCompra(p: CostoDeProducto, precioCatalogo: number, simbolo: string): string | null {
  const costo = p.ultimoCostoCompra;
  if (costo === null || costo === undefined || redondear(costo) <= redondear(p.costoBase)) return null;
  const fecha = p.fechaUltimaCompra ? ` (${p.fechaUltimaCompra.slice(8, 10)}/${p.fechaUltimaCompra.slice(5, 7)})` : '';
  const ultima = `Última compra: ${simbolo} ${dinero(costo)} sin IGV${fecha}.`;
  if (redondear(costo) >= redondear(precioCatalogo)) {
    return `${ultima} El precio de catálogo ${simbolo} ${dinero(precioCatalogo)} quedó por debajo del costo.`;
  }
  return `${ultima} El costo del catálogo es ${simbolo} ${dinero(p.costoBase)}: el precio de ${simbolo} ${dinero(precioCatalogo)} deja menos ganancia.`;
}
