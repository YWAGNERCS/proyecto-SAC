package com.bomerp.facturacion.ose.integration;
import com.bomerp.facturacion.comprobante.entity.Comprobante;
import com.bomerp.facturacion.notacredito.entity.NotaCredito;

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

    /**
     * @param notaCredito La nota de crédito a emitir (referencia al comprobante que anula)
     * @return Los enlaces del PDF y XML, y si fue aceptada por SUNAT.
     */
    RespuestaOse emitirNotaCredito(NotaCredito notaCredito);

    record RespuestaOse(boolean exito, String enlacePdf, String enlaceXml, String mensajeError) {}
}
