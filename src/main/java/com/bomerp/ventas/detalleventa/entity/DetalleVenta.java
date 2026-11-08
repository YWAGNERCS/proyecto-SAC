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

    /**
     * Nombre del producto al vender: es lo que sale en el comprobante (XML y PDF). Se guarda aparte para que
     * renombrar el producto después no cambie un comprobante ya emitido.
     */
    @Column(length = 150)
    private String descripcion;

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

    /** El nombre guardado al vender; en filas sin él (no debería haber tras la migración), el actual del producto. */
    public String getDescripcion() {
        return descripcion != null ? descripcion : (producto != null ? producto.getNombre() : null);
    }

    @PrePersist
    void fijarDescripcion() {
        if (descripcion == null && producto != null) {
            descripcion = producto.getNombre();
        }
    }

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
