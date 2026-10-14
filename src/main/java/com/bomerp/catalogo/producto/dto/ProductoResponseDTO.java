package com.bomerp.catalogo.producto.dto;

import com.bomerp.catalogo.producto.entity.Producto;

import java.math.BigDecimal;

/**
 * Producto para el panel administrativo, con su categoría aplanada (id y nombre):
 * se evita devolver la entidad Categoria completa y cualquier ciclo de serialización.
 */
public record ProductoResponseDTO(
        Long id,
        String nombre,
        String descripcion,
        Long categoriaId,
        String categoriaNombre,
        String sector,
        BigDecimal costoBase,
        BigDecimal margenPorcentaje,
        BigDecimal precioVenta,
        String moneda,
        Integer stockActual,
        Integer stockMinimo,
        boolean visibleEnCatalogo
) {
    public static ProductoResponseDTO desdeEntidad(Producto p) {
        return new ProductoResponseDTO(
                p.getId(),
                p.getNombre(),
                p.getDescripcion(),
                p.getCategoria() != null ? p.getCategoria().getId() : null,
                p.getCategoria() != null ? p.getCategoria().getNombre() : null,
                p.getSector().name(),
                p.getCostoBase(),
                p.getMargenPorcentaje(),
                p.calcularPrecioVenta(),
                p.getMoneda().name(),
                p.getStockActual(),
                p.getStockMinimo(),
                p.isVisibleEnCatalogo()
        );
    }
}
