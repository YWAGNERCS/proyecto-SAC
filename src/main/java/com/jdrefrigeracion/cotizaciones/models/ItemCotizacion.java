package com.jdrefrigeracion.cotizaciones.models;

import com.jdrefrigeracion.catalogo.models.Producto;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Detalle de una cotización: un producto/servicio con su cantidad
 * y el precio unitario "congelado" al momento de cotizar (importante:
 * si el precio del producto cambia después, esta cotización no debe
 * cambiar retroactivamente).
 */
@Entity
@Table(name = "ITEMS_COTIZACION")
@Getter
@Setter
@NoArgsConstructor
public class ItemCotizacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "cotizacion_id", nullable = false)
    private Cotizacion cotizacion;

    @ManyToOne
    @JoinColumn(name = "producto_id", nullable = false)
    @NotNull
    private Producto producto;

    @NotNull
    @Column(nullable = false)
    private Integer cantidad;

    /** Precio unitario al momento de cotizar (copiado de Producto.calcularPrecioVenta()) */
    @NotNull
    @Column(name = "precio_unitario", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioUnitario;

    public ItemCotizacion(Producto producto, Integer cantidad) {
        this.producto = producto;
        this.cantidad = cantidad;
        this.precioUnitario = producto.calcularPrecioVenta(); // se "congela" aquí
    }

    public BigDecimal getPrecioUnitario() {
        return precioUnitario != null ? precioUnitario.setScale(2, java.math.RoundingMode.HALF_UP) : null;
    }

    public BigDecimal calcularSubtotalItem() {
        return getPrecioUnitario().multiply(BigDecimal.valueOf(cantidad)).setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
