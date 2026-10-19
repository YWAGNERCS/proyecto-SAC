package com.bomerp.compras.compra.dto;

import com.bomerp.catalogo.producto.entity.Producto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Compra a un proveedor. El total NO se recibe del navegador: lo calcula el servidor
 * con las líneas (el campo se acepta por compatibilidad y se ignora).
 */
public record CompraRequestDTO(
        @NotNull Long proveedorId,
        @NotNull @PastOrPresent(message = "La fecha de compra no puede ser futura") LocalDate fechaCompra,
        @NotBlank(message = "El N° de factura del proveedor es obligatorio") String numeroFactura,
        @NotNull Producto.Moneda moneda,
        BigDecimal total,
        @NotEmpty(message = "La compra debe tener al menos un producto") List<@Valid DetalleCompraRequestDTO> detalles
) {
    public record DetalleCompraRequestDTO(
            @NotNull Long productoId,
            @NotNull @Min(value = 1, message = "La cantidad debe ser al menos 1") Integer cantidad,
            @NotNull @DecimalMin(value = "0", inclusive = false, message = "El precio debe ser mayor que 0")
            BigDecimal precioUnitarioCompra
    ) {}
}
