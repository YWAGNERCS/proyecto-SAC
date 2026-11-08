package com.bomerp.notificaciones.notificacion.service;

import java.util.List;

/** Envía correos. Devuelve true si el correo salió y false si falló (el error queda en el log). */
public interface NotificadorCorreo {
    boolean enviarCorreo(String destinatario, String asunto, String cuerpo);

    default boolean enviarCorreoConAdjunto(String destinatario, String asunto, String cuerpo, java.io.File adjunto) {
        return enviarCorreo(destinatario, asunto, cuerpo);
    }

    /**
     * Envía un correo HTML con varios adjuntos y devuelve el Message-ID que aceptó el servidor SMTP. A diferencia de
     * los métodos anteriores, NO esconde el error: si el servidor no lo acepta (o falta configurar el SMTP) lanza
     * {@link CorreoNoEnviadoException} con el motivo, para que quien envía lo registre y lo reintente.
     * "Aceptado" significa que el servidor SMTP lo recibió para entregarlo, no que llegó a la bandeja del cliente.
     */
    default String enviarConAdjuntos(MensajeCorreo mensaje) {
        throw new CorreoNoEnviadoException("Este notificador no envía correos con adjuntos.");
    }

    /**
     * @param messageId identificador propio del mensaje (&lt;…@…&gt;); se guarda antes de enviarlo para poder rastrearlo
     */
    record MensajeCorreo(String destinatario, String asunto, String html, List<Adjunto> adjuntos, String messageId) {
        public MensajeCorreo {
            adjuntos = adjuntos == null ? List.of() : List.copyOf(adjuntos);
        }
    }

    /** Un archivo adjunto en memoria (los bytes se envían tal cual, sin transformarlos). */
    record Adjunto(String nombre, byte[] contenido, String tipoMime) {
    }

    /** El servidor SMTP no aceptó el correo (o no está configurado): el mensaje dice por qué. */
    class CorreoNoEnviadoException extends RuntimeException {
        public CorreoNoEnviadoException(String mensaje) {
            super(mensaje);
        }

        public CorreoNoEnviadoException(String mensaje, Throwable causa) {
            super(mensaje, causa);
        }
    }

    /** "a***@gmail.com": para los registros del backend, sin exponer la dirección completa. */
    static String ocultar(String correo) {
        if (correo == null) return null;
        int arroba = correo.indexOf('@');
        if (arroba <= 0) return "***";
        return correo.charAt(0) + "***" + correo.substring(arroba);
    }
}
