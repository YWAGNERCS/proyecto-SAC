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

    /**
     * Por qué el comprobante de esta venta todavía no se puede emitir, o null si se puede. Se pregunta ANTES
     * de darle número: mientras falte un dato de JD (p. ej. la cuenta de detracciones), la venta queda sin
     * comprobante, así no consume un correlativo ni corre el plazo de 3 días para enviarlo a SUNAT.
     */
    default String motivoParaNoEmitir(com.bomerp.ventas.venta.entity.Venta venta) {
        return null;
    }

    /**
     * ¿Se puede llamar a SUNAT ahora? Con el Circuit Breaker abierto (SUNAT no responde), no: el envío queda
     * PENDIENTE sin gastar un intento y sale solo cuando SUNAT vuelve (ADS S11). Llamarlo reserva el turno de la
     * llamada de prueba cuando el circuito está semiabierto: hay que llamar a emitir justo después.
     */
    default boolean disponible() {
        return true;
    }

    /** Aviso para la campana mientras SUNAT no responde, o null. */
    default String avisoNoDisponible() {
        return null;
    }

    /**
     * Lo que este cliente acepta tiene validez tributaria: solo el envío a SUNAT de producción. El simulado y la
     * beta de SUNAT no, así que lo que "aceptan" no se envía por correo al cliente (informe D-12).
     */
    default boolean conValidezTributaria() {
        return ambiente() == com.bomerp.facturacion.comprobante.entity.Comprobante.AmbienteEmision.PRODUCCION;
    }

    /**
     * Ambiente al que este cliente envía hoy: se guarda en cada comprobante al aceptarlo (no se vuelve a preguntar
     * después, porque la configuración puede cambiar). Por defecto, simulado.
     */
    default com.bomerp.facturacion.comprobante.entity.Comprobante.AmbienteEmision ambiente() {
        return com.bomerp.facturacion.comprobante.entity.Comprobante.AmbienteEmision.SIMULADO;
    }

    /**
     * Cómo terminó un envío. REINTENTAR: SUNAT caída, lenta o limitando envíos (no es culpa del comprobante).
     * POR_CONFIRMAR: SUNAT ya tiene registrado ese número por un envío anterior (código 1033), pero no dice si
     * ese envío fue aceptado o rechazado: no tiene validez hasta verificarlo, y reenviarlo solo repetiría el 1033.
     * CREDENCIALES: SUNAT rechazó el usuario o la clave SOL (0101-0106). No es culpa del comprobante ni se arregla
     * reintentando: se detienen todos los envíos para no bloquear la cuenta SOL (informe de rechazos SUNAT, D-9).
     */
    enum Resultado { ACEPTADO, OBSERVADO, RECHAZADO, REINTENTAR, POR_CONFIRMAR, CREDENCIALES }

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
