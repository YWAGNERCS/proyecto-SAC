package com.jdrefrigeracion.notificaciones.services;

import org.springframework.stereotype.Component;

/**
 * Implementación TEMPORAL/simulada (solo imprime en consola) para WhatsApp.
 * El correo ya fue reemplazado por NotificadorCorreoReal.
 */
@Component
public class NotificadorSimulado implements NotificadorWhatsApp {

    @Override
    public void enviarWhatsApp(String telefonoDestino, String mensaje) {
        System.out.println("[SIMULADO - WhatsApp] a " + telefonoDestino + ": " + mensaje);
    }
}
