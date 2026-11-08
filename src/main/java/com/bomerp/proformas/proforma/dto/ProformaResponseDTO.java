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
            BigDecimal subtotalItem,
            /** Equipo de climatización o refrigeración: al convertir se ofrece programar su mantenimiento. */
            boolean requiereMantenimiento,
            /** Precio editado al cotizar (auditoría M1): el de catálogo, quién y por qué; null = de catálogo. */
            BigDecimal precioCatalogo,
            String precioEditadoPor,
            String motivoPrecio
    ) {}

    public static ProformaResponseDTO desdeEntidad(Proforma c) {
        List<ItemResponseDTO> items = c.getItems().stream()
                .map(i -> new ItemResponseDTO(
                        i.getProducto().getNombre(),
                        i.getCantidad(),
                        i.getPrecioUnitario(),
                        i.getDescuentoPorcentaje(),
                        i.calcularSubtotalItem(),
                        i.getProducto().requiereMantenimiento(),
                        i.getPrecioCatalogo(),
                        i.getPrecioEditadoPor(),
                        i.getMotivoPrecio()))
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
