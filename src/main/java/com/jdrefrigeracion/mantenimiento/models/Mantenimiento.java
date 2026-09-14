package com.jdrefrigeracion.mantenimiento.models;

import com.jdrefrigeracion.ventas.models.Venta;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venta_id", nullable = false)
    private Venta venta;

    @Column(name = "fecha_programada", nullable = false)
    private LocalDate fechaProgramada;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoMantenimiento estado = EstadoMantenimiento.PENDIENTE;

    @Column(name = "alerta_enviada", nullable = false)
    private boolean alertaEnviada = false;

    @Column(length = 500)
    private String notas;

    public enum EstadoMantenimiento {
        PENDIENTE, REALIZADO, CANCELADO, VENCIDO
    }
}
