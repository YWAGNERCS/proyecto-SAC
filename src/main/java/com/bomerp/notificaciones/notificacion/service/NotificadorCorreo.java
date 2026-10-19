package com.bomerp.notificaciones.notificacion.service;

/** Envía correos. Devuelve true si el correo salió y false si falló (el error queda en el log). */
public interface NotificadorCorreo {
    boolean enviarCorreo(String destinatario, String asunto, String cuerpo);

    default boolean enviarCorreoConAdjunto(String destinatario, String asunto, String cuerpo, java.io.File adjunto) {
        return enviarCorreo(destinatario, asunto, cuerpo);
    }
}
