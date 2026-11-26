package com.bomerp.catalogo.producto.entity;

import java.math.BigDecimal;

/** Clasificaciones del anexo 3 que utiliza el catálogo de JD. */
public enum ClasificacionDetraccion {
    MANTENIMIENTO_REPARACION("020", "12"),
    OTROS_SERVICIOS_EMPRESARIALES("022", "12"),
    DEMAS_SERVICIOS("037", "12"),
    CONSTRUCCION("030", "4"),
    ARRENDAMIENTO("019", "10"),
    NO_SUJETO(null, "0");

    private final String codigo;
    private final BigDecimal porcentaje;
    ClasificacionDetraccion(String codigo, String porcentaje) {
        this.codigo = codigo;
        this.porcentaje = new BigDecimal(porcentaje);
    }
    public String codigo() { return codigo; }
    public BigDecimal porcentaje() { return porcentaje; }
}
