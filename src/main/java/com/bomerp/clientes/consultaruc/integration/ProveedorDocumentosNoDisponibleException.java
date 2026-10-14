package com.bomerp.clientes.consultaruc.integration;

/**
 * El proveedor externo de RUC/DNI no pudo responder (límite de consultas 429,
 * tiempo de espera agotado o error del servidor). No significa que el número no exista.
 */
public class ProveedorDocumentosNoDisponibleException extends RuntimeException {
    public ProveedorDocumentosNoDisponibleException(String mensaje) {
        super(mensaje);
    }
}
