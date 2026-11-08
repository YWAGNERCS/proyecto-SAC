package com.bomerp.facturacion.correo.dto;

import com.bomerp.facturacion.correo.entity.EnvioCorreo;
import com.bomerp.facturacion.correo.entity.IntentoCorreo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Entrega por correo de un comprobante: lo que ve Lisandro en Ventas. El estado SUNAT y el del correo son cosas
 * distintas: ACEPTADO es de SUNAT; ENVIADO quiere decir que el servidor de correo (SMTP) recibió el mensaje.
 *
 * @param ambienteEmision ambiente de SUNAT en que se aceptó (PRODUCCION, BETA, SIMULADO, NO_REGISTRADO; null si aún no)
 * @param correoCliente   correo registrado del cliente hoy (null si no tiene)
 * @param motivoNoSeEnvia por qué este comprobante no se envía por correo (todavía no válido, beta, sin correo…), o null
 * @param ultimo          el envío más reciente (null si nunca se pidió)
 * @param envios          cuántos envíos se pidieron (automático y reenvíos)
 * @param ultimaAceptacionSmtp la última vez que el servidor de correo aceptó uno (null si nunca)
 * @param historial       todos los envíos con sus intentos (solo en la consulta del detalle; vacío en la lista)
 */
public record EntregaCorreoDTO(Long comprobanteId, String ambienteEmision, String correoCliente, String motivoNoSeEnvia, Envio ultimo, int envios,
                               LocalDateTime ultimaAceptacionSmtp, List<Envio> historial) {

    public record Envio(Long id, String destinatario, String tipo, String estado, String solicitadoPor,
                        LocalDateTime fechaCreacion, LocalDateTime fechaUltimoIntento, LocalDateTime fechaAceptacionSmtp,
                        int intentos, String ultimoError, String messageId, LocalDateTime proximoIntento,
                        List<Intento> intentosDetalle) {

        public static Envio de(EnvioCorreo e, List<IntentoCorreo> intentos) {
            return new Envio(e.getId(), e.getDestinatario(), e.getTipo().name(), e.getEstado().name(), e.getSolicitadoPor(),
                    e.getFechaCreacion(), e.getFechaUltimoIntento(), e.getFechaAceptacionSmtp(), e.getIntentos(),
                    e.getUltimoError(), e.getMessageId(), e.getProximoIntento(),
                    intentos.stream().map(Intento::de).toList());
        }
    }

    public record Intento(int intento, LocalDateTime inicio, LocalDateTime fin, String resultado, String mensaje,
                          String messageId, String adjuntos) {
        static Intento de(IntentoCorreo i) {
            return new Intento(i.getIntento(), i.getInicio(), i.getFin(), i.getResultado().name(), i.getMensaje(),
                    i.getMessageId(), i.getAdjuntos());
        }
    }
}
