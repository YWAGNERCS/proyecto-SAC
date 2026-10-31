package com.bomerp.ventas.detalleventa.entity;
import com.bomerp.ventas.venta.entity.Venta;

import com.bomerp.catalogo.producto.entity.Producto;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "DETALLE_VENTA")
@Getter
@Setter
@NoArgsConstructor
public class DetalleVenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venta_id", nullable = false)
    private Venta venta;

    @ManyToOne
    @JoinColumn(name = "producto_id", nullable = false)
    @NotNull
    private Producto producto;

    @NotNull
    @Column(nullable = false)
    private Integer cantidad;

    @NotNull
    @Column(name = "precio_unitario", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioUnitario;

    /** Descuento negociado de la línea (%), 0 si no hay. Nulo en filas anteriores = 0. */
    @Column(name = "descuento_porcentaje", precision = 5, scale = 2)
    private BigDecimal descuentoPorcentaje;

    /**
     * Costo unitario del producto al vender, en la moneda de la venta (rentabilidad). Lo pone
     * Venta.agregarDetalle; en ventas anteriores a la migración es el costo de ese momento.
     * Null si no se pudo convertir (venta en otra moneda sin tipo de cambio).
     */
    @Column(name = "costo_unitario", precision = 12, scale = 2)
    private BigDecimal costoUnitario;

    public DetalleVenta(Producto producto, Integer cantidad, BigDecimal precioUnitario) {
        this(producto, cantidad, precioUnitario, BigDecimal.ZERO);
    }

    public DetalleVenta(Producto producto, Integer cantidad, BigDecimal precioUnitario, BigDecimal descuentoPorcentaje) {
        this.producto = producto;
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario;
        this.descuentoPorcentaje = descuentoPorcentaje;
    }

    public BigDecimal getDescuentoPorcentaje() {
        return descuentoPorcentaje != null ? descuentoPorcentaje : BigDecimal.ZERO;
    }

    public BigDecimal getPrecioUnitario() {
        return precioUnitario != null ? precioUnitario.setScale(2, java.math.RoundingMode.HALF_UP) : null;
    }

    /** Precio × cantidad, menos el descuento de la línea. */
    public BigDecimal calcularSubtotalDetalle() {
        BigDecimal factor = BigDecimal.ONE.subtract(getDescuentoPorcentaje().movePointLeft(2));
        return getPrecioUnitario().multiply(BigDecimal.valueOf(cantidad)).multiply(factor)
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
