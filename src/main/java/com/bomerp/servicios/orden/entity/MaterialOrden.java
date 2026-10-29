package com.bomerp.servicios.orden.entity;

import com.bomerp.catalogo.producto.entity.Producto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Material usado en una orden de trabajo (tabla intermedia OrdenTrabajo ↔ Producto, con su cantidad
 * y el costo al momento de usarlo). Al cerrar la orden descuenta el stock y sale en el kardex.
 */
@Entity
@Table(name = "MATERIALES_ORDEN")
@Getter
@Setter
@NoArgsConstructor
public class MaterialOrden {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orden_id", nullable = false)
    private OrdenTrabajo orden;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(nullable = false)
    private Integer cantidad;

    /** Costo del material al usarlo (costo base del producto), para el costo de la orden. */
    @Column(name = "costo_unitario", nullable = false, precision = 12, scale = 2)
    private BigDecimal costoUnitario;

    @Column(nullable = false, length = 10)
    private String moneda;

    public MaterialOrden(Producto producto, Integer cantidad) {
        this.producto = producto;
        this.cantidad = cantidad;
        this.costoUnitario = producto.getCostoBase();
        this.moneda = producto.getMoneda().name();
    }
}
