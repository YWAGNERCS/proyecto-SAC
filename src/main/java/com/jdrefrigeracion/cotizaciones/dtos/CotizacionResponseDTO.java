package com.jdrefrigeracion.cotizaciones.dtos;
import com.jdrefrigeracion.cotizaciones.models.Cotizacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CotizacionResponseDTO(
        Long id,
        String clienteNombre,
        LocalDate fechaEmision,
        LocalDate fechaVencimiento,
        String estado,
        String moneda,
        BigDecimal subtotal,
        BigDecimal igv,
        BigDecimal total,
        List<ItemResponseDTO> items
) {
    public record ItemResponseDTO(
            String productoNombre,
            Integer cantidad,
            BigDecimal precioUnitario,
            BigDecimal subtotalItem
    ) {}

    public static CotizacionResponseDTO desdeEntidad(Cotizacion c) {
        List<ItemResponseDTO> items = c.getItems().stream()
                .map(i -> new ItemResponseDTO(
                        i.getProducto().getNombre(),
                        i.getCantidad(),
                        i.getPrecioUnitario(),
                        i.calcularSubtotalItem()))
                .toList();

        return new CotizacionResponseDTO(
                c.getId(),
                c.getCliente().getNombreORazonSocial(),
                c.getFechaEmision(),
                c.getFechaVencimiento(),
                c.getEstado().name(),
                c.getMoneda().name(),
                c.calcularSubtotal(),
                c.calcularIgv(),
                c.calcularTotal(),
                items
        );
    }
}
