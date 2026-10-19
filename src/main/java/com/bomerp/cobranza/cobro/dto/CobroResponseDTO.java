package com.bomerp.cobranza.cobro.dto;

import com.bomerp.cobranza.cobro.entity.Cobro;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CobroResponseDTO(
        Long id,
        Long clienteId,
        String clienteNombre,
        LocalDate fecha,
        String moneda,
        String metodoPago,
        String referencia,
        String notas,
        BigDecimal montoTotal,
        List<AbonoResponseDTO> abonos
) {
    public record AbonoResponseDTO(Long ventaId, BigDecimal monto) {}

    public static CobroResponseDTO desdeEntidad(Cobro c) {
        return new CobroResponseDTO(
                c.getId(),
                c.getCliente().getId(),
                c.getCliente().getNombreORazonSocial(),
                c.getFecha(),
                c.getMoneda().name(),
                c.getMetodoPago().name(),
                c.getReferencia(),
                c.getNotas(),
                c.getMontoTotal(),
                c.getDetalles().stream()
                        .map(d -> new AbonoResponseDTO(d.getVenta().getId(), d.getMonto()))
                        .toList());
    }
}
