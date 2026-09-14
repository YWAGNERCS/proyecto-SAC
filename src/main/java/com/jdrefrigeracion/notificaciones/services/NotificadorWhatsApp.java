package com.jdrefrigeracion.notificaciones.services;

/**
 * Aplicando el principio I (Segregación de Interfaces, Sesión 3):
 * interfaces pequeñas y específicas por canal, en vez de una sola
 * interfaz gigante que obligue a implementar métodos que no se usan.
 */
public interface NotificadorWhatsApp {
    void enviarWhatsApp(String telefonoDestino, String mensaje);
}
