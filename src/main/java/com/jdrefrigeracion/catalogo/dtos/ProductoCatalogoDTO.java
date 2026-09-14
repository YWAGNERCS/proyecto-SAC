package com.jdrefrigeracion.catalogo.dtos;
import com.jdrefrigeracion.catalogo.models.Producto;

import java.math.BigDecimal;

/**
 * DTO (Data Transfer Object) para el catálogo público.
 *
 * Importante: el catálogo público NO debe exponer el costo base
 * ni el margen de ganancia (eso es información interna del negocio).
 * Solo se muestra el precio final ya calculado.
 */
public record ProductoCatalogoDTO(
        Long id,
        String nombre,
        String descripcion,
        String sector,
        BigDecimal precioVenta,
        String moneda
) {
    public static ProductoCatalogoDTO desdeEntidad(Producto producto) {
        return new ProductoCatalogoDTO(
                producto.getId(),
                producto.getNombre(),
                producto.getDescripcion(),
                producto.getSector().name(),
                producto.calcularPrecioVenta(),
                producto.getMoneda().name()
        );
    }
}
