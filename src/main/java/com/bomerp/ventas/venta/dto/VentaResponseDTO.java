package com.bomerp.ventas.venta.dto;
import com.bomerp.ventas.venta.entity.Venta;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record VentaResponseDTO(
        Long id,
        Long clienteId,
        String clienteNombre,
        Long proformaOrigenId,
        LocalDate fechaVenta,
        String tipoPago,
        String estado,
        String estadoPago,
        LocalDate fechaLimitePago,
        String moneda,
        BigDecimal tipoCambio,
        BigDecimal subtotal,
        BigDecimal igv,
        BigDecimal total,
        Long comprobanteId,
        String registradoPor,
        List<DetalleResponseDTO> detalles
) {
    public record DetalleResponseDTO(
            String productoNombre,
            Integer cantidad,
            BigDecimal precioUnitario,
            BigDecimal descuentoPorcentaje,
            BigDecimal subtotalDetalle
    ) {}

    public static VentaResponseDTO desdeEntidad(Venta v) {
        List<DetalleResponseDTO> detalles = v.getDetalles().stream()
                .map(d -> new DetalleResponseDTO(
                        d.getProducto().getNombre(),
                        d.getCantidad(),
                        d.getPrecioUnitario(),
                        d.getDescuentoPorcentaje(),
                        d.calcularSubtotalDetalle()))
                .toList();

        return new VentaResponseDTO(
                v.getId(),
                v.getCliente().getId(),
                v.getCliente().getNombreORazonSocial(),
                v.getProformaOrigen() != null ? v.getProformaOrigen().getId() : null,
                v.getFechaVenta(),
                v.getTipoPago().name(),
                v.getEstado().name(),
                v.getEstadoPago().name(),
                v.getFechaLimitePago(),
                v.getMoneda().name(),
                v.getTipoCambio(),
                v.calcularSubtotal(),
                v.calcularIgv(),
                v.calcularTotal(),
                v.getComprobanteId(),
                v.getVendedorNombre(),
                detalles
        );
    }
}
