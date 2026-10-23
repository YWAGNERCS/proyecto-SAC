package com.bomerp.catalogo.producto.entity;

import com.bomerp.catalogo.categoria.entity.Categoria;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

/**
 * Producto/servicio que ofrece JD Refrigeración.
 * Cubre tanto equipos (climatización, refrigeración, ventilación forzada)
 * como materiales de servicio (tuberías, aislamiento, planchas galvanizadas).
 */
@Entity
@Table(name = "PRODUCTOS")
@Getter
@Setter
@NoArgsConstructor
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(length = 500)
    private String descripcion;

    /** Sector: CLIMATIZACION, REFRIGERACION, VENTILACION_FORZADA */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Sector sector;

    /** Costo base, antes de aplicar el margen de ganancia */
    @NotNull
    @Column(name = "costo_base", nullable = false, precision = 12, scale = 2)
    private BigDecimal costoBase;

    /** Margen de ganancia como porcentaje, ej. 20 = 20% */
    @NotNull
    @Column(name = "margen_porcentaje", nullable = false, precision = 5, scale = 2)
    private BigDecimal margenPorcentaje;

    /** Moneda del costo base: SOLES o DOLARES */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Moneda moneda;

    /** Solo aplica a materiales de servicio; los equipos no manejan stock propio */
    @Column(name = "stock_actual")
    private Integer stockActual;

    @Column(name = "stock_minimo")
    private Integer stockMinimo;

    // NUMBER(1) con CHECK (0,1), como lo creó Hibernate 6. Sin BIT, Hibernate 7 en Oracle 23 lo convertiría a BOOLEAN.
    @Column(name = "visible_en_catalogo", nullable = false)
    @JdbcTypeCode(SqlTypes.BIT)
    private boolean visibleEnCatalogo = true;

    /**
     * Categoría del catálogo (relación Categoría–Producto, S3).
     * Admite nulo solo por los productos registrados antes de esta relación;
     * al crear o editar por la API la categoría es obligatoria.
     */
    @ManyToOne
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    public enum Sector {
        CLIMATIZACION, REFRIGERACION, VENTILACION_FORZADA, MATERIAL_SERVICIO
    }

    public enum Moneda {
        SOLES, DOLARES
    }

    /**
     * Calcula el precio de venta aplicando el margen de ganancia
     * sobre el costo base. La misma lógica que Lisandro describió:
     * costo 1000, margen 20% -> precio final 1200.
     */
    public BigDecimal calcularPrecioVenta() {
        BigDecimal margenDecimal = margenPorcentaje.divide(BigDecimal.valueOf(100));
        BigDecimal incremento = costoBase.multiply(margenDecimal);
        return costoBase.add(incremento);
    }

    /**
     * Solo los materiales de servicio llevan control estricto de stock: los equipos
     * se compran y venden por proyecto y no se almacenan (acuerdo con el cliente).
     * No es un getter (sin prefijo get/is), así que no se serializa ni se persiste.
     */
    public boolean controlaStock() {
        return sector == Sector.MATERIAL_SERVICIO;
    }
}
