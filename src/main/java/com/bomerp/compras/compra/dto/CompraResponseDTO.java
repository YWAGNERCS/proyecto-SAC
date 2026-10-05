package com.bomerp.compras.compra.dto;

import com.bomerp.compras.compra.entity.Compra;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CompraResponseDTO(
        Long id,
        ProveedorDTO proveedor,
        LocalDate fechaCompra,
        String numeroFactura,
        String moneda,
        BigDecimal total,
        List<DetalleCompraResponseDTO> detalles
) {
    public record ProveedorDTO(
            Long id,
            String nombreORazonSocial
    ) {}

    public record ProductoDTO(
            Long id,
            String nombre
    ) {}

    public record DetalleCompraResponseDTO(
            Long id,
            ProductoDTO producto,
            Integer cantidad,
            BigDecimal precioUnitarioCompra
    ) {}

    public static CompraResponseDTO desdeEntidad(Compra c) {
        ProveedorDTO provDto = c.getProveedor() != null
                ? new ProveedorDTO(c.getProveedor().getId(), c.getProveedor().getNombreORazonSocial())
                : null;

        List<DetalleCompraResponseDTO> detalles = c.getDetalles() != null
                ? c.getDetalles().stream()
                .map(d -> new DetalleCompraResponseDTO(
                        d.getId(),
                        d.getProducto() != null ? new ProductoDTO(d.getProducto().getId(), d.getProducto().getNombre()) : null,
                        d.getCantidad(),
                        d.getPrecioUnitarioCompra()
                ))
                .toList()
                : List.of();

        return new CompraResponseDTO(
                c.getId(),
                provDto,
                c.getFechaCompra(),
                c.getNumeroFactura(),
                c.getMoneda() != null ? c.getMoneda().name() : null,
                c.getTotal(),
                detalles
        );
    }
}
