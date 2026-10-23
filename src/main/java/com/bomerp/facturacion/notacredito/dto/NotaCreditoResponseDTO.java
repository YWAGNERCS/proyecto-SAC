package com.bomerp.facturacion.notacredito.dto;

import com.bomerp.facturacion.notacredito.entity.NotaCredito;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Nota de crédito para la API: el comprobante que anula se muestra por su número, y la venta por su id. */
public record NotaCreditoResponseDTO(
        Long id,
        String serie,
        String numero,
        LocalDate fechaEmision,
        String motivo,
        BigDecimal total,
        String estadoSunat,
        String enlacePdf,
        String enlaceXml,
        String comprobanteAnulado,
        Long ventaId
) {
    public static NotaCreditoResponseDTO desdeEntidad(NotaCredito n) {
        var comprobante = n.getComprobanteReferencia();
        return new NotaCreditoResponseDTO(
                n.getId(),
                n.getSerie(),
                n.getNumero(),
                n.getFechaEmision(),
                n.getMotivo(),
                n.getTotal(),
                n.getEstadoSunat() != null ? n.getEstadoSunat().name() : null,
                n.getEnlacePdf(),
                n.getEnlaceXml(),
                n.getNumeroComprobanteReferencia(),
                comprobante != null && comprobante.getVenta() != null ? comprobante.getVenta().getId() : null
        );
    }
}
