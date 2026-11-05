package com.bomerp.notificaciones.notificacion.service;

import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementación TEMPORAL/simulada (solo imprime en consola) para WhatsApp.
 * El correo ya fue reemplazado por NotificadorCorreoReal.
 */
@Slf4j
@Component
public class NotificadorSimulado implements NotificadorWhatsApp {

    @Override
    public void enviarWhatsApp(String telefonoDestino, String mensaje) {
        log.debug("[SIMULADO - WhatsApp] a {}: {}", telefonoDestino, mensaje);
    }
}
