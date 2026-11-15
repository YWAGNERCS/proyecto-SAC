package com.bomerp.config.empresa;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * Datos de JD Refrigeración S.A.C. que se imprimen en comprobantes y proformas.
 * Se configuran en application.properties (prefijo {@code app.empresa}), no en el código.
 */
@ConfigurationProperties(prefix = "app.empresa")
public record EmpresaProperties(
        String razonSocial,
        String ruc,
        String direccion,
        /** Cuenta de detracciones en el Banco de la Nación (se imprime en facturas con detracción). */
        String cuentaDetracciones,
        /**
         * Contacto oficial de JD para el correo del comprobante (opcionales: lo que esté vacío no se muestra; no se
         * inventa ningún dato). Los confirma Lisandro.
         */
        String telefono,
        String correoContacto,
        String web,
        /** Cuenta corriente BCP y CCI para proformas y transferencias. */
        String cuentaBcp,
        String cciBcp,
        /** Cuenta Banco de la Nación para proformas. */
        String cuentaNacion
) {
    @ConstructorBinding
    public EmpresaProperties {
    }

    /** Constructor con datos de contacto (para pruebas que no usan cuentas bancarias). */
    public EmpresaProperties(String razonSocial, String ruc, String direccion, String cuentaDetracciones,
                             String telefono, String correoContacto, String web) {
        this(razonSocial, ruc, direccion, cuentaDetracciones, telefono, correoContacto, web, null, null, null);
    }

    /** Sin datos de contacto ni cuentas secundarias (pruebas y usos que no los necesitan). */
    public EmpresaProperties(String razonSocial, String ruc, String direccion, String cuentaDetracciones) {
        this(razonSocial, ruc, direccion, cuentaDetracciones, null, null, null, null, null, null);
    }
}
