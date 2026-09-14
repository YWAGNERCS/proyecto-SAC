package com.jdrefrigeracion.ventas.dtos;
import com.jdrefrigeracion.ventas.models.Venta;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record VentaResponseDTO(
        Long id,
        String clienteNombre,
        Long cotizacionOrigenId,
        LocalDate fechaVenta,
        String tipoPago,
        String estadoPago,
        LocalDate fechaLimitePago,
        String moneda,
        BigDecimal subtotal,
        BigDecimal igv,
        BigDecimal total,
        List<DetalleResponseDTO> detalles
) {
    public record DetalleResponseDTO(
            String productoNombre,
            Integer cantidad,
            BigDecimal precioUnitario,
            BigDecimal subtotalDetalle
    ) {}

    public static VentaResponseDTO desdeEntidad(Venta v) {
        List<DetalleResponseDTO> detalles = v.getDetalles().stream()
                .map(d -> new DetalleResponseDTO(
                        d.getProducto().getNombre(),
                        d.getCantidad(),
                        d.getPrecioUnitario(),
                        d.calcularSubtotalDetalle()))
                .toList();

        return new VentaResponseDTO(
                v.getId(),
                v.getCliente().getNombreORazonSocial(),
                v.getCotizacionOrigen() != null ? v.getCotizacionOrigen().getId() : null,
                v.getFechaVenta(),
                v.getTipoPago().name(),
                v.getEstadoPago().name(),
                v.getFechaLimitePago(),
                v.getMoneda().name(),
                v.calcularSubtotal(),
                v.calcularIgv(),
                v.calcularTotal(),
                detalles
        );
    }
}
