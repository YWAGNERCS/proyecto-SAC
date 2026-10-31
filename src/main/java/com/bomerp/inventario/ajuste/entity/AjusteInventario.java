package com.bomerp.inventario.ajuste.entity;

import com.bomerp.catalogo.producto.entity.Producto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Movimiento de stock que no es compra, venta ni orden de trabajo: conteo físico, merma, material
 * dañado, robo o pérdida, devolución al proveedor, stock inicial u otro. Guarda el stock antes y
 * después para que el kardex y el historial cuenten lo mismo. No se edita ni se borra: un ajuste
 * equivocado se corrige con otro ajuste.
 */
@Entity
@Table(name = "AJUSTES_INVENTARIO")
@Getter
@Setter
@NoArgsConstructor
public class AjusteInventario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(nullable = false)
    private LocalDate fecha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Tipo tipo;

    @Column(nullable = false)
    private Integer cantidad;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Motivo motivo;

    @Column(length = 300)
    private String observacion;

    @Column(name = "stock_anterior", nullable = false)
    private Integer stockAnterior;

    @Column(name = "stock_nuevo", nullable = false)
    private Integer stockNuevo;

    @Column(name = "registrado_por", length = 100)
    private String registradoPor;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro;

    public enum Tipo {
        ENTRADA, SALIDA
    }

    public enum Motivo {
        CONTEO_FISICO("Conteo físico"),
        STOCK_INICIAL("Stock inicial"),
        MERMA("Merma"),
        DANADO("Dañado"),
        ROBO_PERDIDA("Robo o pérdida"),
        DEVOLUCION_PROVEEDOR("Devolución al proveedor"),
        OTRO("Otro");

        private final String nombre;

        Motivo(String nombre) {
            this.nombre = nombre;
        }

        public String getNombre() {
            return nombre;
        }
    }
}
