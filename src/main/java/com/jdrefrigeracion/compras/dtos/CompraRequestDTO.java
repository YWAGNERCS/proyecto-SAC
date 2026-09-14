package com.jdrefrigeracion.compras.dtos;

import com.jdrefrigeracion.catalogo.models.Producto;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CompraRequestDTO(
        @NotNull Long proveedorId,
        @NotNull LocalDate fechaCompra,
        String numeroFactura,
        @NotNull Producto.Moneda moneda,
        @NotNull BigDecimal total,
        @NotEmpty List<DetalleCompraRequestDTO> detalles
) {
    public record DetalleCompraRequestDTO(
            @NotNull Long productoId,
            @NotNull Integer cantidad,
            @NotNull BigDecimal precioUnitarioCompra
    ) {}
}
