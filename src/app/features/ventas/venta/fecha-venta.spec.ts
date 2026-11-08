import { DIAS_FECHA_PASADA, errorFechaVenta, fechaLarga, fechaMinimaVenta, fechaParaEnviar, hoyIso } from './fecha-venta';

describe('Fecha real de la venta (auditoría M3)', () => {
  const hoy = new Date(2026, 10, 7, 21, 30);   // 7 de noviembre, de noche (en UTC ya es el 8)

  it('hoy y la fecha mínima, en hora local', () => {
    expect(hoyIso(hoy)).toBe('2026-11-07');
    expect(DIAS_FECHA_PASADA).toBe(30);
    expect(fechaMinimaVenta(hoy)).toBe('2026-10-08');
  });

  it('acepta de hoy hasta 30 días atrás; rechaza futuras y más antiguas', () => {
    expect(errorFechaVenta('2026-11-07', hoy)).toBeNull();
    expect(errorFechaVenta('2026-10-08', hoy)).toBeNull();
    expect(errorFechaVenta('', hoy)).toBeNull();
    expect(errorFechaVenta('2026-11-08', hoy)).toBe('La fecha de la venta no puede ser futura.');
    expect(errorFechaVenta('2026-10-07', hoy)).toBe('La fecha de la venta puede ser de hasta 30 días atrás.');
  });

  it('solo se envía la fecha si es pasada (hoy o vacía = null: el backend pone hoy)', () => {
    expect(fechaParaEnviar('2026-11-07', hoy)).toBeNull();
    expect(fechaParaEnviar(null, hoy)).toBeNull();
    expect(fechaParaEnviar('2026-11-01', hoy)).toBe('2026-11-01');
    expect(fechaLarga('2026-11-01')).toBe('01/11/2026');
  });
});
