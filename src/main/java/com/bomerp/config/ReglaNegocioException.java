package com.bomerp.config;

/** Violación de una regla de negocio (se responde 409). */
public class ReglaNegocioException extends RuntimeException {
    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
