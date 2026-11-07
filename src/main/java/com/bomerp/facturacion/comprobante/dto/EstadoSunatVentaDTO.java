package com.bomerp.facturacion.comprobante.dto;

import com.bomerp.facturacion.comprobante.entity.Comprobante;
import com.bomerp.facturacion.comprobante.entity.EnvioSunat;
import com.bomerp.facturacion.notacredito.entity.NotaCredito;

/**
 * Estado ante SUNAT del comprobante de una venta y, si se anuló, de su nota de crédito. Lo pide la
 * lista de Ventas para mostrar si cada documento fue aceptado, observado, rechazado o sigue pendiente.
 */
public record EstadoSunatVentaDTO(Long ventaId, Documento comprobante, Documento notaCredito) {

    /**
     * @param tieneXml hay XML firmado guardado (para descargarlo)
     * @param tieneCdr hay constancia de recepción de SUNAT guardada
     * @param simulado  "aceptado" por el envío simulado: nunca llegó a SUNAT y no tiene validez tributaria
     */
    public record Documento(Long id, String numero, String estado, Integer codigo, String mensaje,
                            int intentos, boolean tieneXml, boolean tieneCdr, boolean simulado) {

        static Documento de(Long id, String serie, String numero, Comprobante.EstadoSunat estado, EnvioSunat envio) {
            // Simulado: los emitidos antes del envío real (aceptados sin ningún intento) o por el modo simulado
            boolean simulado = estado.valido() && envio.getCdr() == null
                    && (envio.getIntentos() == 0 || (envio.getMensaje() != null && envio.getMensaje().startsWith("Simulado")));
            return new Documento(id, serie + "-" + Integer.parseInt(numero), estado.name(), envio.getCodigo(), envio.getMensaje(),
                    envio.getIntentos(), envio.getXmlFirmado() != null, envio.getCdr() != null, simulado);
        }
    }

    public static EstadoSunatVentaDTO de(Comprobante c, NotaCredito nota) {
        return new EstadoSunatVentaDTO(c.getVenta().getId(),
                Documento.de(c.getId(), c.getSerie(), c.getNumero(), c.getEstadoSunat(), c.getEnvio()),
                nota == null ? null : Documento.de(nota.getId(), nota.getSerie(), nota.getNumero(), nota.getEstadoSunat(), nota.getEnvio()));
    }
}
