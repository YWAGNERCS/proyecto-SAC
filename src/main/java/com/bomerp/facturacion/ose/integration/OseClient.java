package com.bomerp.facturacion.ose.integration;
import com.bomerp.facturacion.comprobante.entity.Comprobante;

/**
 * Principio D (SOLID) - Inversión de Dependencias.
 * Nuestro sistema principal NO depende de Nubefact, SUNAT o Factiliza.
 * Depende únicamente de esta interfaz.
 */
public interface OseClient {
    
    /**
     * @param comprobante El comprobante a emitir
     * @return Los enlaces del PDF y XML, y si fue aceptado por SUNAT.
     */
    RespuestaOse emitir(Comprobante comprobante);

    record RespuestaOse(boolean exito, String enlacePdf, String enlaceXml, String mensajeError) {}
}
