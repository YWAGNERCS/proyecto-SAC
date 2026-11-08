import { EntregaCorreo, EnvioCorreo } from '../../../core/services/facturacion-service';
import { confirmacionReenvio, detalleEntrega, etiquetaAmbiente, etiquetaCorreo, puedeReenviarCorreo, yaSeEnvio } from './entrega-correo';

function envio(parcial: Partial<EnvioCorreo>): EnvioCorreo {
  return {
    id: 1, destinatario: 'compras@cliente.pe', tipo: 'AUTOMATICO', estado: 'ENVIADO', solicitadoPor: null,
    fechaCreacion: '2026-11-08T10:30:00', fechaUltimoIntento: '2026-11-08T10:30:00', fechaAceptacionSmtp: '2026-11-08T10:30:05',
    intentos: 1, ultimoError: null, messageId: '<a@b>', proximoIntento: null, intentosDetalle: [], ...parcial,
  };
}

function entrega(parcial: Partial<EntregaCorreo>): EntregaCorreo {
  return {
    comprobanteId: 9, correoCliente: 'compras@cliente.pe', motivoNoSeEnvia: null, ultimo: null, envios: 0,
    ultimaAceptacionSmtp: null, historial: [], ...parcial,
  };
}

describe('Entrega del comprobante por correo', () => {
  it('el estado del correo no se confunde con el de SUNAT', () => {
    expect(etiquetaCorreo('ENVIADO')).toBe('Entregado al servidor de correo');
    expect(etiquetaCorreo('FALLIDO')).toBe('No se pudo enviar');
    expect(etiquetaCorreo('INCIERTO')).toBe('Envío por confirmar');
  });

  it('muestra el ambiente en que SUNAT lo aceptó (solo producción tiene validez)', () => {
    expect(etiquetaAmbiente('PRODUCCION')).toBe('Producción');
    expect(etiquetaAmbiente('BETA')).toBe('Beta (pruebas, sin validez)');
    expect(etiquetaAmbiente('NO_REGISTRADO')).toBe('No registrado');
    expect(etiquetaAmbiente(null)).toBeNull();
  });

  it('dice cuándo salió, cuándo se reintenta o por qué no sale', () => {
    const enviado = entrega({ ultimo: envio({}), envios: 1, ultimaAceptacionSmtp: '2026-11-08T10:30:05' });
    expect(detalleEntrega(enviado)).toBe('El 08/11/2026 10:30 (automático)');
    expect(detalleEntrega(entrega({ ultimo: envio({ estado: 'FALLIDO', fechaAceptacionSmtp: null, proximoIntento: '2026-11-08T10:42:00' }) })))
      .toBe('Se vuelve a intentar solo el 08/11/2026 10:42');
    expect(detalleEntrega(entrega({ motivoNoSeEnvia: 'El cliente no tiene correo registrado.' }))).toBe('El cliente no tiene correo registrado.');
  });

  it('solo se ofrece reenviar si se puede y no hay otro envío en curso', () => {
    expect(puedeReenviarCorreo(entrega({}))).toBe(true);
    expect(puedeReenviarCorreo(entrega({ motivoNoSeEnvia: 'beta de SUNAT' }))).toBe(false);
    expect(puedeReenviarCorreo(entrega({ ultimo: envio({ estado: 'EN_PROCESO' }) }))).toBe(false);
    expect(puedeReenviarCorreo(entrega({ ultimo: envio({ estado: 'FALLIDO' }) }))).toBe(true);
    expect(puedeReenviarCorreo(null)).toBe(false);
  });

  it('reenviar uno ya enviado o incierto advierte que puede llegar dos veces', () => {
    const enviado = entrega({ ultimo: envio({}), ultimaAceptacionSmtp: '2026-11-08T10:30:05' });
    expect(yaSeEnvio(enviado)).toBe(true);
    expect(confirmacionReenvio(enviado, 'F001-125')).toContain('ya se entregó al servidor de correo el 08/11/2026 10:30');
    expect(confirmacionReenvio(enviado, 'F001-125')).toContain('lo recibirá otra vez');
    const incierto = entrega({ ultimo: envio({ estado: 'INCIERTO', fechaAceptacionSmtp: null }) });
    expect(confirmacionReenvio(incierto, 'F001-125')).toContain('no se sabe si le llegó');
    expect(confirmacionReenvio(entrega({}), 'F001-125')).toBe('¿Enviar F001-125 (PDF y XML) a compras@cliente.pe?');
  });
});
