package com.bomerp.servicios.orden.entity;

import com.bomerp.clientes.general.integration.Cliente;
import com.bomerp.clientes.sede.entity.SedeCliente;
import com.bomerp.postventa.equipo.entity.EquipoInstalado;
import com.bomerp.ventas.venta.entity.Venta;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Orden de trabajo: el trabajo técnico en el local del cliente (instalación, mantenimiento,
 * reparación o visita). Se programa con técnico, fecha y dirección; al cerrarla se registra lo hecho,
 * quién lo recibió y los materiales usados (que descuentan stock). Después se puede facturar como servicio.
 */
@Entity
@Table(name = "ORDENES_TRABAJO")
@Getter
@Setter
@NoArgsConstructor
public class OrdenTrabajo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Tipo tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Estado estado = Estado.PROGRAMADA;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sede_id")
    private SedeCliente sede;

    /** Venta que originó el trabajo (p. ej. la instalación de los equipos vendidos). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venta_id")
    private Venta venta;

    /** Visita de mantenimiento programada que ejecuta esta orden. */
    @Column(name = "mantenimiento_id")
    private Long mantenimientoId;

    /** Equipo instalado sobre el que se trabaja (reparación, visita o mantenimiento). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipo_id")
    private EquipoInstalado equipo;

    @Column(name = "fecha_programada", nullable = false)
    private LocalDate fechaProgramada;

    @Column(nullable = false, length = 100)
    private String tecnico;

    @Column(length = 200)
    private String direccion;

    /** Qué hay que hacer. */
    @Column(nullable = false, length = 500)
    private String descripcion;

    // ── Cierre ──
    @Column(name = "fecha_realizada")
    private LocalDate fechaRealizada;

    /** Qué se hizo (lo escribe el técnico al cerrar). */
    @Column(name = "trabajo_realizado", length = 1000)
    private String trabajoRealizado;

    /** Quién dio la conformidad en el local (nombre y, si se tiene, DNI o cargo). */
    @Column(name = "recibido_por", length = 100)
    private String recibidoPor;

    @Column(length = 500)
    private String observaciones;

    @OneToMany(mappedBy = "orden", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MaterialOrden> materiales = new ArrayList<>();

    /** Venta con la que se facturó el servicio (null mientras no se facture). */
    @Column(name = "venta_facturada_id")
    private Long ventaFacturadaId;

    @Column(name = "registrado_por", length = 100)
    private String registradoPor;

    @Column(name = "cerrado_por", length = 100)
    private String cerradoPor;

    @Column(name = "motivo_cancelacion", length = 300)
    private String motivoCancelacion;

    public enum Tipo {
        INSTALACION, MANTENIMIENTO, REPARACION, VISITA_TECNICA
    }

    public enum Estado {
        PROGRAMADA, REALIZADA, CANCELADA
    }

    public void agregarMaterial(MaterialOrden material) {
        materiales.add(material);
        material.setOrden(this);
    }
}
