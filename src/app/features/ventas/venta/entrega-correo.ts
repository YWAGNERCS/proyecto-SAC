import { EntregaCorreo, EnvioCorreo, EstadoCorreo } from '../../../core/services/facturacion-service';

/**
 * Entrega del comprobante por correo (reglas de pantalla, funciones puras con pruebas). El estado de SUNAT y el del
 * correo son cosas distintas: "Aceptado" es de SUNAT; "Entregado al servidor de correo" quiere decir que el servidor
 * SMTP recibió el mensaje, no que el cliente ya lo leyó.
 */

export function etiquetaCorreo(estado: EstadoCorreo): string {
  switch (estado) {
    case 'PENDIENTE': return 'Por enviar';
    case 'EN_PROCESO': return 'Enviando';
    case 'ENVIADO': return 'Entregado al servidor de correo';
    case 'FALLIDO': return 'No se pudo enviar';
    case 'INCIERTO': return 'Envío por confirmar';
  }
}

export function claseCorreo(estado: EstadoCorreo): string {
  switch (estado) {
    case 'ENVIADO': return 'badge badge-entregado';
    case 'FALLIDO': return 'badge badge-danger';
    case 'INCIERTO': return 'badge badge-warning';
    default: return 'badge badge-neutro';
  }
}

/** "automático" / "reenvío de lisandro" */
export function origenEnvio(e: EnvioCorreo): string {
  return e.tipo === 'AUTOMATICO' ? 'automático' : `reenvío${e.solicitadoPor ? ' de ' + e.solicitadoPor : ''}`;
}

/** Fecha y hora local corta: 08/11/2026 10:35. */
export function fechaHora(iso: string | null | undefined): string {
  if (!iso) return '';
  return `${iso.slice(8, 10)}/${iso.slice(5, 7)}/${iso.slice(0, 4)} ${iso.slice(11, 16)}`;
}

/** Línea de detalle bajo el estado: cuándo salió, o cuándo se reintenta, o por qué no sale. */
export function detalleEntrega(c: EntregaCorreo): string | null {
  const u = c.ultimo;
  if (!u) return c.motivoNoSeEnvia;
  if (u.estado === 'ENVIADO') return `El ${fechaHora(u.fechaAceptacionSmtp)} (${origenEnvio(u)})`;
  if (u.estado === 'FALLIDO' && u.proximoIntento) return `Se vuelve a intentar solo el ${fechaHora(u.proximoIntento)}`;
  return null;
}

/**
 * Se ofrece "Reenviar por correo" si el comprobante se puede enviar (válido y con correo) y no hay un envío en curso.
 * La autorización real la decide el servidor.
 */
export function puedeReenviarCorreo(c: EntregaCorreo | null | undefined): boolean {
  if (!c || c.motivoNoSeEnvia) return false;
  return !c.ultimo || (c.ultimo.estado !== 'PENDIENTE' && c.ultimo.estado !== 'EN_PROCESO');
}

/** Ya hay un envío que el servidor aceptó (o que quedó por confirmar): reenviar exige confirmarlo expresamente. */
export function yaSeEnvio(c: EntregaCorreo): boolean {
  return !!c.ultimaAceptacionSmtp || c.ultimo?.estado === 'INCIERTO';
}

/** Texto del diálogo de confirmación del reenvío. */
export function confirmacionReenvio(c: EntregaCorreo, numero: string): string {
  const a = c.correoCliente ?? 'el correo del cliente';
  if (c.ultimo?.estado === 'INCIERTO') {
    return `El último envío de ${numero} se interrumpió y no se sabe si le llegó al cliente.\n\n` +
      `Si lo reenvía, puede recibirlo dos veces. ¿Reenviar ${numero} a ${a}?`;
  }
  if (yaSeEnvio(c)) {
    return `${numero} ya se entregó al servidor de correo el ${fechaHora(c.ultimaAceptacionSmtp)}.\n\n` +
      `El cliente lo recibirá otra vez. ¿Reenviar ${numero} (PDF y XML) a ${a}?`;
  }
  return `¿Enviar ${numero} (PDF y XML) a ${a}?`;
}
