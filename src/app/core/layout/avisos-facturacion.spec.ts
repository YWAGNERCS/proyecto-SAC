import { PlazoEnvio } from '../services/facturacion-service';
import { avisosDeFacturacion, plural, primeros } from './avisos-facturacion';

/** Resumen del plazo de envío sin nada pendiente; cada prueba agrega lo que necesita. */
function plazo(cambios: Partial<PlazoEnvio> = {}): PlazoEnvio {
  return {
    porVencer: 0, fueraDePlazo: 0, numerosPorVencer: [], numerosFueraDePlazo: [],
    porEmitir: 0, ventasPorEmitir: [], envioDetenido: null,
    ...cambios,
  };
}

describe('Avisos de facturación en la campana', () => {
  it('sin nada pendiente, o si Facturación no respondió, no hay avisos', () => {
    expect(avisosDeFacturacion(plazo())).toEqual([]);
    expect(avisosDeFacturacion(null)).toEqual([]);
  });

  it('comprobantes por vencer y fuera de plazo (informe D-6), con sus números', () => {
    const avisos = avisosDeFacturacion(plazo({
      porVencer: 1, numerosPorVencer: ['F001-131'],
      fueraDePlazo: 2, numerosFueraDePlazo: ['F001-120', 'B001-80'],
    }));
    expect(avisos.map((a) => a.titulo)).toEqual(['2 comprobantes fuera de plazo', '1 comprobante sin enviar a SUNAT']);
    expect(avisos[0].detalle).toContain('emita un comprobante de reemplazo (F001-120, B001-80)');
    expect(avisos[1].detalle).toBe('El plazo vence hoy o mañana (F001-131)');
    expect(avisos.every((a) => a.nivel === 'alta' && a.ruta === '/ventas')).toBe(true);
  });

  it('envíos detenidos por usuario o clave SOL (informe D-9)', () => {
    const [aviso] = avisosDeFacturacion(plazo({ envioDetenido: 'SUNAT rechazó el usuario o la clave SOL (código 102)' }));
    expect(aviso.titulo).toBe('Envíos a SUNAT detenidos');
    expect(aviso.detalle).toContain('corríjalos y reinicie');
  });

  it('ventas por emitir por falta de la cuenta de detracciones, con "…" si son más de tres', () => {
    const [aviso] = avisosDeFacturacion(plazo({ porEmitir: 4, ventasPorEmitir: [425, 426, 427, 428] }));
    expect(aviso.titulo).toBe('4 ventas sin comprobante');
    expect(aviso.detalle).toBe('Falta configurar la cuenta de detracciones para emitirlo (venta #425, #426, #427…)');
  });

  it('plural y primeros', () => {
    expect(plural(1, 'venta', 'ventas')).toBe('1 venta');
    expect(plural(0, 'venta', 'ventas')).toBe('0 ventas');
    expect(primeros(['a', 'b'])).toBe('a, b');
    expect(primeros(['a', 'b', 'c', 'd'])).toBe('a, b, c…');
  });
});
