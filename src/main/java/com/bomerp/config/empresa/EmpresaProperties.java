package com.bomerp.config.empresa;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Datos de JD Refrigeración S.A.C. que se imprimen en comprobantes y proformas.
 * Se configuran en application.properties (prefijo {@code app.empresa}), no en el código.
 */
@ConfigurationProperties(prefix = "app.empresa")
public record EmpresaProperties(
        String razonSocial,
        String ruc,
        String direccion
) {
}
