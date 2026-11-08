import { avisoUltimaCompra, errorPrecioEditado, textoPrecioEditado } from './precio-editado';

describe('Precio editado de una línea (auditoría M1)', () => {
  it('solo sube, y con motivo de 5 a 200 caracteres', () => {
    expect(errorPrecioEditado(525, 560, 'Subió el costo del proveedor')).toBeNull();
    expect(errorPrecioEditado(525, 525, 'Subió el costo')).toContain('solo puede subir');
    expect(errorPrecioEditado(525, 500, 'Cliente frecuente')).toContain('use el descuento');
    expect(errorPrecioEditado(525, 560, '  caro ')).toContain('de 5 a 200');
    expect(errorPrecioEditado(525, 560, 'x'.repeat(201))).toContain('de 5 a 200');
    expect(errorPrecioEditado(525, null, 'Subió el costo')).toBe('Escriba el nuevo precio.');
  });

  it('muestra las dos cifras, quién lo editó y por qué', () => {
    expect(textoPrecioEditado({ precioUnitario: 560, precioCatalogo: 525, precioEditadoPor: 'lucy', motivoPrecio: 'subió el costo' }, 'S/'))
      .toBe('Precio de catálogo S/ 525.00, se vendió a S/ 560.00 (editado por lucy: subió el costo)');
    expect(textoPrecioEditado({ precioUnitario: 525 }, 'S/')).toBeNull();
  });

  it('avisa cuando la última compra salió más cara que el costo del catálogo', () => {
    // Costo 500, margen 5 % → precio 525. Última compra a 560 sin IGV: por debajo del costo
    expect(avisoUltimaCompra({ costoBase: 500, ultimoCostoCompra: 560, fechaUltimaCompra: '2026-11-05' }, 525, 'S/'))
      .toBe('Última compra: S/ 560.00 sin IGV (05/11). El precio de catálogo S/ 525.00 quedó por debajo del costo.');
    // A 510: todavía gana, pero menos
    expect(avisoUltimaCompra({ costoBase: 500, ultimoCostoCompra: 510, fechaUltimaCompra: null }, 525, 'S/'))
      .toContain('deja menos ganancia');
    // Igual o más barata, o nunca comprada: nada que avisar
    expect(avisoUltimaCompra({ costoBase: 500, ultimoCostoCompra: 480 }, 525, 'S/')).toBeNull();
    expect(avisoUltimaCompra({ costoBase: 500, ultimoCostoCompra: null }, 525, 'S/')).toBeNull();
  });
});
