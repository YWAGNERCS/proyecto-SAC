package com.bomerp.catalogo.producto.dto;

import com.bomerp.catalogo.producto.entity.Producto;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Datos para crear o editar un producto. La categoría llega solo como id:
 * el servicio verifica que exista (404 si no) antes de asociarla.
 */
public record ProductoRequestDTO(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 150, message = "El nombre no puede exceder 150 caracteres")
        String nombre,

        @Size(max = 500, message = "La descripción no puede exceder 500 caracteres")
        String descripcion,

        @NotNull(message = "La categoría es obligatoria")
        Long categoriaId,

        @NotNull(message = "El sector es obligatorio")
        Producto.Sector sector,

        @NotNull(message = "El costo base es obligatorio")
        @DecimalMin(value = "0.00", message = "El costo base no puede ser negativo")
        BigDecimal costoBase,

        @NotNull(message = "El margen es obligatorio")
        @DecimalMin(value = "0.00", message = "El margen no puede ser negativo")
        BigDecimal margenPorcentaje,

        @NotNull(message = "La moneda es obligatoria")
        Producto.Moneda moneda,

        @Min(value = 0, message = "El stock no puede ser negativo")
        Integer stockActual,

        @Min(value = 0, message = "El stock mínimo no puede ser negativo")
        Integer stockMinimo,

        Boolean visibleEnCatalogo
) {}
