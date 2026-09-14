package com.jdrefrigeracion.clientes.integrations;

/**
 * Abstracción para consultar datos de una empresa a partir de su RUC.
 *
 * NOTA (Principio D - Inversión de Dependencias, igual que en Facturación):
 * el servicio de Clientes depende de esta INTERFAZ, no de una implementación
 * concreta. Hoy se puede implementar contra una API pública (ej. APIs Perú,
 * Factiliza, o el propio padrón de SUNAT), y el día de mañana se puede
 * cambiar de proveedor sin tocar el resto del código.
 */
public interface ConsultaRucClient {

    /**
     * @param ruc  RUC de 11 dígitos a consultar
     * @return datos básicos de la empresa, o null si no se encontró
     */
    DatosRuc consultar(String ruc);

    /**
     * @param dni  DNI de 8 dígitos a consultar
     * @return datos básicos de la persona, o null si no se encontró
     */
    DatosDni consultarDni(String dni);

    record DatosRuc(String ruc, String razonSocial, String direccion) {}
    record DatosDni(String dni, String nombres, String apellidoPaterno, String apellidoMaterno) {}
}
