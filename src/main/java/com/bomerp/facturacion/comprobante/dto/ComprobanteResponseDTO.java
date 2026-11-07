package com.bomerp.facturacion.comprobante.dto;

import com.bomerp.facturacion.comprobante.entity.Comprobante;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Respuesta de "emitir comprobante": los datos del comprobante y la venta a la que pertenece (solo su id). */
public record ComprobanteResponseDTO(
        Long id,
        String tipo,
        String serie,
        String numero,
        LocalDate fechaEmision,
        BigDecimal total,
        String estadoSunat,
        /** Código y mensaje de SUNAT del último envío (motivo del rechazo u observaciones) e intentos. */
        Integer sunatCodigo,
        String sunatMensaje,
        int sunatIntentos,
        String enlacePdf,
        String enlaceXml,
        Long ventaId
) {
    public static ComprobanteResponseDTO desdeEntidad(Comprobante c) {
        return new ComprobanteResponseDTO(
                c.getId(),
                c.getTipo() != null ? c.getTipo().name() : null,
                c.getSerie(),
                c.getNumero(),
                c.getFechaEmision(),
                c.getTotal(),
                c.getEstadoSunat() != null ? c.getEstadoSunat().name() : null,
                c.getEnvio().getCodigo(),
                c.getEnvio().getMensaje(),
                c.getEnvio().getIntentos(),
                c.getEnlacePdf(),
                c.getEnlaceXml(),
                c.getVenta() != null ? c.getVenta().getId() : null
        );
    }
}
