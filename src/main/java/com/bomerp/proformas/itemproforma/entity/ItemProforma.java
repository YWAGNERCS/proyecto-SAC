package com.bomerp.proformas.itemproforma.entity;
import com.bomerp.proformas.proforma.entity.Proforma;

import com.bomerp.catalogo.producto.entity.Producto;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Detalle de una proforma: un producto/servicio con su cantidad
 * y el precio unitario "congelado" al momento de cotizar (importante:
 * si el precio del producto cambia después, esta proforma no debe
 * cambiar retroactivamente).
 */
@Entity
@Table(name = "ITEMS_PROFORMA")
@Getter
@Setter
@NoArgsConstructor
public class ItemProforma {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "proforma_id", nullable = false)
    private Proforma proforma;

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

    /** Descuento negociado de la línea (%), 0 si no hay. Nulo en filas anteriores = 0. */
    @Column(name = "descuento_porcentaje", precision = 5, scale = 2)
    private BigDecimal descuentoPorcentaje;

    /**
     * Precio de catálogo de la línea (en la moneda del documento) cuando el precio se editó al registrarla
     * (auditoría M1); null = se cobró el de catálogo. Con precio_unitario quedan las dos cifras.
     */
    @Column(name = "precio_catalogo", precision = 12, scale = 2)
    private BigDecimal precioCatalogo;

    /** Quién editó el precio (null = precio de catálogo). */
    @Column(name = "precio_editado_por", length = 100)
    private String precioEditadoPor;

    /** Por qué se editó el precio. */
    @Column(name = "motivo_precio", length = 200)
    private String motivoPrecio;

    /** Guarda la edición del precio (si la hubo); el precio cobrado se fija aparte. */
    public void aplicarPrecio(com.bomerp.config.PrecioDeLinea precio) {
        this.precioUnitario = precio.precioUnitario();
        if (precio.editado()) {
            this.precioCatalogo = precio.precioCatalogo();
            this.precioEditadoPor = precio.editadoPor();
            this.motivoPrecio = precio.motivo();
        }
    }

    public boolean precioEditado() {
        return precioEditadoPor != null;
    }

    public ItemProforma(Producto producto, Integer cantidad) {
        this.producto = producto;
        this.cantidad = cantidad;
        this.precioUnitario = producto.calcularPrecioVenta(); // se "congela" aquí
    }

    /** Con el precio ya convertido a la moneda de la proforma (también queda congelado). */
    public ItemProforma(Producto producto, Integer cantidad, BigDecimal precioUnitario) {
        this(producto, cantidad, precioUnitario, BigDecimal.ZERO);
    }

    public ItemProforma(Producto producto, Integer cantidad, BigDecimal precioUnitario, BigDecimal descuentoPorcentaje) {
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
    public BigDecimal calcularSubtotalItem() {
        BigDecimal factor = BigDecimal.ONE.subtract(getDescuentoPorcentaje().movePointLeft(2));
        return getPrecioUnitario().multiply(BigDecimal.valueOf(cantidad)).multiply(factor)
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
