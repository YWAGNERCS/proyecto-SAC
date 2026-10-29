package com.bomerp.postventa.mantenimiento.entity;

import com.bomerp.clientes.general.integration.Cliente;
import com.bomerp.postventa.contrato.entity.ContratoMantenimiento;
import com.bomerp.postventa.equipo.entity.EquipoInstalado;
import com.bomerp.ventas.venta.entity.Venta;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Visita de mantenimiento preventivo. Se programa por una de tres vías:
 *  - VENTA:    4 visitas (3, 6, 9 y 12 meses) al vender equipos de climatización o refrigeración.
 *  - EQUIPO:   según la frecuencia elegida para un equipo instalado (cada 1, 2, 3, 6 o 12 meses).
 *  - CONTRATO: según la frecuencia de un contrato de mantenimiento, entre sus fechas.
 */
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
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    /** Venta que lo originó (null si viene de un equipo instalado o de un contrato). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venta_id")
    private Venta venta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipo_id")
    private EquipoInstalado equipo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contrato_id")
    private ContratoMantenimiento contrato;

    /** Técnico asignado o que hizo la visita. */
    @Column(length = 100)
    private String tecnico;

    /** Orden de trabajo con la que se ejecutó (null si se marcó realizado sin orden). */
    @Column(name = "orden_trabajo_id")
    private Long ordenTrabajoId;

    @Column(name = "fecha_programada", nullable = false)
    private LocalDate fechaProgramada;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoMantenimiento estado = EstadoMantenimiento.PENDIENTE;

    @Column(name = "alerta_enviada", nullable = false)
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
