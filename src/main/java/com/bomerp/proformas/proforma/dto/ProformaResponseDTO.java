package com.bomerp.proformas.proforma.dto;
import com.bomerp.proformas.proforma.entity.Proforma;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ProformaResponseDTO(
        Long id,
        String clienteNombre,
        LocalDate fechaEmision,
        LocalDate fechaVencimiento,
        String estado,
        String moneda,
        java.math.BigDecimal tipoCambio,
        BigDecimal subtotal,
        BigDecimal igv,
        BigDecimal total,
        Long ventaId,
        boolean enviadoCorreo,
        boolean enviadoWhatsApp,
        List<ItemResponseDTO> items
) {
    public record ItemResponseDTO(
            String productoNombre,
            Integer cantidad,
            BigDecimal precioUnitario,
            BigDecimal descuentoPorcentaje,
            BigDecimal subtotalItem
    ) {}

    public static ProformaResponseDTO desdeEntidad(Proforma c) {
        List<ItemResponseDTO> items = c.getItems().stream()
                .map(i -> new ItemResponseDTO(
                        i.getProducto().getNombre(),
                        i.getCantidad(),
                        i.getPrecioUnitario(),
                        i.getDescuentoPorcentaje(),
                        i.calcularSubtotalItem()))
                .toList();

        return new ProformaResponseDTO(
                c.getId(),
                c.getCliente().getNombreORazonSocial(),
                c.getFechaEmision(),
                c.getFechaVencimiento(),
                c.getEstado().name(),
                c.getMoneda().name(),
                c.getTipoCambio(),
                c.calcularSubtotal(),
                c.calcularIgv(),
                c.calcularTotal(),
                c.getVentaId(),
                c.isEnviadoCorreo(),
                c.isEnviadoWhatsApp(),
                items
        );
    }
}
