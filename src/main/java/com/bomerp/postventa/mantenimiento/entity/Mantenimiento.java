package com.bomerp.postventa.mantenimiento.entity;

import com.bomerp.ventas.venta.entity.Venta;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;

@Entity
@Table(name = "MANTENIMIENTOS")
@Getter
@Setter
@NoArgsConstructor
public class Mantenimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venta_id", nullable = false)
    private Venta venta;

    // Getter expuesto para el JSON
    @com.fasterxml.jackson.annotation.JsonProperty("ventaId")
    public Long getVentaId() {
        return this.venta != null ? this.venta.getId() : null;
    }

    @Column(name = "fecha_programada", nullable = false)
    private LocalDate fechaProgramada;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoMantenimiento estado = EstadoMantenimiento.PENDIENTE;

    // NUMBER(1) con CHECK (0,1), como lo creó Hibernate 6. Sin BIT, Hibernate 7 en Oracle 23 lo convertiría a BOOLEAN.
    @Column(name = "alerta_enviada", nullable = false)
    @JdbcTypeCode(SqlTypes.BIT)
    private boolean alertaEnviada = false;

    @Column(length = 500)
    private String notas;

    /** Día en que se hizo la visita (solo si estado = REALIZADO). */
    @Column(name = "fecha_realizada")
    private LocalDate fechaRealizada;

    public enum EstadoMantenimiento {
        PENDIENTE, REALIZADO, CANCELADO, VENCIDO
    }
}
