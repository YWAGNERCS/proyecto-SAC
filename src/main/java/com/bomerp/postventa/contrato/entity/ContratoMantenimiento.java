package com.bomerp.postventa.contrato.entity;

import com.bomerp.clientes.general.integration.Cliente;
import com.bomerp.clientes.sede.entity.SedeCliente;
import com.bomerp.ventas.venta.entity.Venta;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Contrato de mantenimiento: entre dos fechas, JD visita al cliente (o a una de sus sedes) cada
 * {@code frecuenciaMeses} meses y cobra {@code montoVisita} por visita. Las visitas se programan al crearlo.
 */
@Entity
@Table(name = "CONTRATOS_MANTENIMIENTO")
@Getter
@Setter
@NoArgsConstructor
public class ContratoMantenimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    /** Sede cubierta (null = todos los locales del cliente). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sede_id")
    private SedeCliente sede;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;

    @Column(name = "frecuencia_meses", nullable = false)
    private Integer frecuenciaMeses;

    /** Precio de cada visita, sin IGV. */
    @Column(name = "monto_visita", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoVisita;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Venta.Moneda moneda;

    /** Qué cubre (equipos, alcance de cada visita). */
    @Column(nullable = false, length = 300)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Estado estado = Estado.VIGENTE;

    @Column(name = "registrado_por", length = 100)
    private String registradoPor;

    @Column(name = "motivo_cancelacion", length = 300)
    private String motivoCancelacion;

    public enum Estado {
        VIGENTE, CANCELADO
    }
}
