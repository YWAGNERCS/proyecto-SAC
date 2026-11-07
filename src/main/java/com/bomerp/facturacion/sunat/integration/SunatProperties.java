package com.bomerp.facturacion.sunat.integration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Envío directo a SUNAT (SEE - Del Contribuyente). Solo se usa con app.facturacion.modo=sunat.
 *
 * @param url              servicio billService: beta (pruebas, sin efecto tributario) o producción
 * @param usuario          usuario SOL secundario con permiso de comprobantes electrónicos, sin el RUC
 *                         (en beta: MODDATOS). Se envía como RUC + usuario.
 * @param clave            clave de ese usuario SOL (en beta: MODDATOS)
 * @param certificadoRuta  archivo .p12/.pfx con el certificado digital que firma los comprobantes
 * @param certificadoClave contraseña del certificado
 */
@ConfigurationProperties(prefix = "app.sunat")
public record SunatProperties(
        String url,
        String usuario,
        String clave,
        String certificadoRuta,
        String certificadoClave
) {
}
