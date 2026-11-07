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

    /** Cómo terminó un envío. REINTENTAR: SUNAT caída, lenta o limitando envíos (no es culpa del comprobante). */
    enum Resultado { ACEPTADO, OBSERVADO, RECHAZADO, REINTENTAR }

    /**
     * Respuesta de SUNAT (o del OSE) a un envío.
     *
     * @param codigo     código de SUNAT: 0 aceptado, 2000-3999 rechazo, 4000+ observación; null si no hubo respuesta
     * @param mensaje    descripción de SUNAT (motivo del rechazo u observaciones), o el error de conexión
     * @param xmlFirmado XML tal como se firmó y envió (se guarda como respaldo legal)
     * @param cdr        constancia de recepción de SUNAT (ZIP), cuando la hay
     */
    record RespuestaOse(Resultado resultado, Integer codigo, String mensaje, String enlacePdf, String enlaceXml,
                        byte[] xmlFirmado, byte[] cdr) {

        /** Aceptado, con o sin observaciones: el comprobante tiene validez. */
        public boolean exito() {
            return resultado == Resultado.ACEPTADO || resultado == Resultado.OBSERVADO;
        }

        public static RespuestaOse reintentar(String motivo, byte[] xmlFirmado) {
            return new RespuestaOse(Resultado.REINTENTAR, null, motivo, null, null, xmlFirmado, null);
        }
    }
}
