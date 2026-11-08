import { DocumentoSunat, EstadoSunatVenta } from '../../../core/services/facturacion-service';
import {
  avisoPlazo, claseSunat, documentosSunat, etiquetaCorta, etiquetaResultado, etiquetaSunat, fechaCorta, isoLocal,
  motivoReemplazo, porConfirmar, puedeReenviar,
} from './sunat-documento';

/** Documento de SUNAT de prueba: un comprobante pendiente sin intentos; cada prueba cambia lo que necesita. */
function doc(cambios: Partial<DocumentoSunat> = {}): DocumentoSunat {
  return {
    id: 1, numero: 'F001-133', estado: 'PENDIENTE', codigo: null, mensaje: null, intentos: 0,
    tieneXml: false, tieneCdr: false, simulado: false, rechazoDefinitivo: false, puedeReemplazar: false,
    fechaLimiteEnvio: null, fueraDePlazo: false,
    ...cambios,
  };
}

describe('Comprobante electrónico en Ventas (sunat-documento)', () => {
  describe('etiquetas', () => {
    it('distingue aceptado, observado, pendiente y simulado', () => {
      expect(etiquetaSunat(doc({ estado: 'ACEPTADO', codigo: 0 }))).toBe('Aceptado por SUNAT');
      expect(etiquetaSunat(doc({ estado: 'OBSERVADO', codigo: 4252 }))).toBe('Aceptado con observaciones');
      expect(etiquetaSunat(doc())).toBe('Pendiente de envío');
      expect(etiquetaSunat(doc({ estado: 'ACEPTADO', simulado: true }))).toBe('Simulado (sin envío a SUNAT)');
      expect(etiquetaCorta(doc({ estado: 'ACEPTADO', simulado: true }))).toBe('SUNAT: simulado');
    });

    it('no confunde un rechazo con constancia con una excepción que SUNAT no recibió (informe D-9)', () => {
      const rechazado = doc({ estado: 'RECHAZADO', codigo: 2800, rechazoDefinitivo: true });
      const noRecibido = doc({ estado: 'RECHAZADO', codigo: 305 });
      expect(etiquetaSunat(rechazado)).toBe('Rechazado por SUNAT: no es válido');
      expect(etiquetaSunat(noRecibido)).toBe('SUNAT no lo recibió: corrija y reenvíe');
      expect(etiquetaCorta(rechazado)).toBe('SUNAT: rechazado');
      expect(etiquetaCorta(noRecibido)).toBe('SUNAT: no recibido');
    });

    it('el 1033 queda por confirmar, no aceptado (informe D-1)', () => {
      const yaRegistrado = doc({ estado: 'PENDIENTE', codigo: 1033, intentos: 1 });
      expect(porConfirmar(yaRegistrado)).toBe(true);
      expect(etiquetaSunat(yaRegistrado)).toBe('Por confirmar en SUNAT');
      expect(etiquetaCorta(yaRegistrado)).toBe('SUNAT: por confirmar');
      expect(porConfirmar(doc({ estado: 'ACEPTADO', codigo: 1033 }))).toBe(false);
    });

    it('fuera de plazo no es válido y va en rojo (informe D-6)', () => {
      const vencido = doc({ fueraDePlazo: true, fechaLimiteEnvio: '2026-11-06', puedeReemplazar: true });
      expect(etiquetaSunat(vencido)).toBe('Fuera de plazo: no es válido');
      expect(etiquetaCorta(vencido)).toBe('SUNAT: fuera de plazo');
      expect(claseSunat(vencido)).toBe('badge-danger');
    });

    it('un reemplazado que nunca llegó a SUNAT no dice "pendiente"', () => {
      expect(etiquetaSunat(doc({ intentos: 1 }), true)).toBe('No llegó a SUNAT a tiempo');
      // uno rechazado con constancia sigue diciendo que SUNAT lo rechazó
      expect(etiquetaSunat(doc({ estado: 'RECHAZADO', codigo: 2800, rechazoDefinitivo: true }), true))
        .toBe('Rechazado por SUNAT: no es válido');
    });

    it('colores: verde aceptado, rojo rechazado, amarillo pendiente, gris simulado', () => {
      expect(claseSunat(doc({ estado: 'ACEPTADO' }))).toBe('badge-success');
      expect(claseSunat(doc({ estado: 'RECHAZADO' }))).toBe('badge-danger');
      expect(claseSunat(doc())).toBe('badge-warning');
      expect(claseSunat(doc({ estado: 'ACEPTADO', simulado: true }))).toBe('badge-neutro');
    });
  });

  describe('Reenviar o reemplazar', () => {
    it('se reenvía lo que SUNAT no recibió y lo pendiente con intentos', () => {
      expect(puedeReenviar(doc({ estado: 'RECHAZADO', codigo: 305 }))).toBe(true);
      expect(puedeReenviar(doc({ intentos: 3 }))).toBe(true);
    });

    it('no se reenvía lo recién emitido, lo aceptado ni lo por confirmar', () => {
      expect(puedeReenviar(doc())).toBe(false);
      expect(puedeReenviar(doc({ estado: 'ACEPTADO', intentos: 1 }))).toBe(false);
      expect(puedeReenviar(doc({ codigo: 1033, intentos: 1 }))).toBe(false);
    });

    it('lo rechazado con constancia, lo vencido y lo que pide reemplazo no se reenvía con su número (D-2, D-6, D-11)', () => {
      expect(puedeReenviar(doc({ estado: 'RECHAZADO', codigo: 2800, rechazoDefinitivo: true, puedeReemplazar: true }))).toBe(false);
      expect(puedeReenviar(doc({ intentos: 2, fueraDePlazo: true, puedeReemplazar: true }))).toBe(false);
      // excepción de un cliente que cambió de tipo: el backend ofrece el reemplazo
      expect(puedeReenviar(doc({ estado: 'RECHAZADO', codigo: 305, puedeReemplazar: true }))).toBe(false);
    });

    it('explica por qué se ofrece el reemplazo en cada caso', () => {
      expect(motivoReemplazo(doc({ estado: 'RECHAZADO', codigo: 2800, rechazoDefinitivo: true })))
        .toContain('SUNAT rechazó F001-133 (código 2800)');
      expect(motivoReemplazo(doc({ fueraDePlazo: true, fechaLimiteEnvio: '2026-11-06' })))
        .toContain('plazo de 3 días (vencía el 06/11)');
      expect(motivoReemplazo(doc({ estado: 'RECHAZADO', codigo: 305 }))).toContain('el cliente cambió de tipo');
    });
  });

  describe('plazo de envío (3 días)', () => {
    const hoy = new Date(2026, 10, 7);   // 7 de noviembre, hora local

    it('avisa hasta qué día SUNAT lo recibe, y el último día que vence hoy', () => {
      expect(avisoPlazo(doc({ fechaLimiteEnvio: '2026-11-09' }), hoy)).toBe('Enviar a SUNAT a más tardar el 09/11');
      expect(avisoPlazo(doc({ fechaLimiteEnvio: '2026-11-07' }), hoy)).toBe('Vence hoy el plazo para enviarlo a SUNAT');
    });

    it('sin plazo pendiente, vencido o simulado no hay aviso', () => {
      expect(avisoPlazo(doc(), hoy)).toBeNull();
      expect(avisoPlazo(doc({ fechaLimiteEnvio: '2026-11-06', fueraDePlazo: true }), hoy)).toBeNull();
      expect(avisoPlazo(doc({ fechaLimiteEnvio: '2026-11-09', simulado: true }), hoy)).toBeNull();
    });

    it('usa la fecha local, no la de UTC (de noche en Lima ya es mañana en UTC)', () => {
      expect(isoLocal(new Date(2026, 10, 7, 23, 30))).toBe('2026-11-07');
      expect(fechaCorta('2026-11-09')).toBe('09/11');
      expect(fechaCorta(null)).toBe('');
    });
  });

  describe('líneas de la venta', () => {
    const estado = (cambios: Partial<EstadoSunatVenta>): EstadoSunatVenta =>
      ({ ventaId: 409, comprobante: doc({ id: 2, numero: 'F001-134' }), notaCredito: null, reemplazados: [], porEmitir: null, ...cambios });

    it('el comprobante vigente, su nota y después los reemplazados, del más reciente al más antiguo', () => {
      const lineas = documentosSunat(estado({
        notaCredito: doc({ id: 9, numero: 'FC01-5' }),
        reemplazados: [doc({ id: 1, numero: 'F001-131' }), doc({ id: 3, numero: 'F001-133' })],
      }));
      expect(lineas.map((l) => `${l.titulo} ${l.d.numero}`)).toEqual([
        'Comprobante F001-134', 'Nota de crédito FC01-5', 'Reemplazado F001-133', 'Reemplazado F001-131',
      ]);
      expect(lineas.map((l) => l.reemplazado)).toEqual([false, false, true, true]);
      expect(lineas[1].esNota).toBe(true);
    });

    it('una venta por emitir (sin cuenta de detracciones) no tiene líneas: se muestra aparte', () => {
      expect(documentosSunat(estado({ comprobante: null, porEmitir: 'Falta configurar la cuenta de detracciones' }))).toEqual([]);
    });
  });

  it('historial: cada resultado en palabras, también el de usuario o clave SOL rechazados', () => {
    const envio = (resultado: string) => ({ id: 1, intento: 1, fecha: '', resultado, codigo: null, mensaje: null, tieneXml: false, tieneCdr: false });
    expect(etiquetaResultado(envio('REINTENTAR'))).toBe('Sin respuesta de SUNAT');
    expect(etiquetaResultado(envio('POR_CONFIRMAR'))).toBe('Ya registrado: por confirmar');
    expect(etiquetaResultado(envio('CREDENCIALES'))).toBe('Usuario o clave SOL rechazados');
    // los envíos copiados por la migración guardan el estado del comprobante
    expect(etiquetaResultado(envio('PENDIENTE'))).toBe('Pendiente (no se envió)');
    expect(etiquetaResultado(envio('OTRO'))).toBe('OTRO');
  });
});
