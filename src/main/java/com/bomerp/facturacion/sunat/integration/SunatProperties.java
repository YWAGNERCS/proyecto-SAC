package com.bomerp.facturacion.sunat.integration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Envío directo a SUNAT (SEE - Del Contribuyente). Solo se usa con app.facturacion.modo=sunat.
 *
 * @param ambiente         PRODUCCION o BETA (SUNAT_AMBIENTE), obligatorio: no se deduce de la URL (ver AmbienteSunat)
 * @param url              servicio billService oficial de ese ambiente (AmbienteSunat.URL_PRODUCCION o URL_BETA)
 * @param usuario          usuario SOL secundario con permiso de comprobantes electrónicos, sin el RUC
 *                         (en beta: MODDATOS). Se envía como RUC + usuario.
 * @param clave            clave de ese usuario SOL (en beta: MODDATOS)
 * @param certificadoRuta  archivo .p12/.pfx con el certificado digital que firma los comprobantes
 * @param certificadoClave contraseña del certificado
 */
@ConfigurationProperties(prefix = "app.sunat")
public record SunatProperties(
        String ambiente,
        String url,
        String usuario,
        String clave,
        String certificadoRuta,
        String certificadoClave
) {
}
