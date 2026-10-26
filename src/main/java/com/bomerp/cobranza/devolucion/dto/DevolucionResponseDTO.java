package com.bomerp.cobranza.devolucion.dto;

import com.bomerp.cobranza.devolucion.entity.Devolucion;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DevolucionResponseDTO(
        Long id,
        Long ventaId,
        Long clienteId,
        String clienteNombre,
        String moneda,
        BigDecimal monto,
        String estado,
        LocalDate fechaAnulacion,
        LocalDate fechaDevolucion,
        String metodoPago,
        String referencia,
        String notas
) {
    public static DevolucionResponseDTO desdeEntidad(Devolucion d) {
        return new DevolucionResponseDTO(
                d.getId(),
                d.getVenta().getId(),
                d.getCliente().getId(),
                d.getCliente().getNombreORazonSocial(),
                d.getMoneda().name(),
                d.getMonto(),
                d.getEstado().name(),
                d.getFechaAnulacion(),
                d.getFechaDevolucion(),
                d.getMetodoPago() != null ? d.getMetodoPago().name() : null,
                d.getReferencia(),
                d.getNotas());
    }
}
