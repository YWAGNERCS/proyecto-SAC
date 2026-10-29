package com.bomerp.postventa.equipo.entity;

import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.clientes.general.integration.Cliente;
import com.bomerp.clientes.sede.entity.SedeCliente;
import com.bomerp.ventas.venta.entity.Venta;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Equipo instalado en el local de un cliente (parque instalado): qué equipo, en qué sede y ubicación,
 * con qué número de serie, desde cuándo y hasta cuándo tiene garantía, y cada cuánto se le da mantenimiento.
 */
@Entity
@Table(name = "EQUIPOS_INSTALADOS")
@Getter
@Setter
@NoArgsConstructor
public class EquipoInstalado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sede_id")
    private SedeCliente sede;

    /** El modelo de equipo del catálogo (climatización, refrigeración o ventilación). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(name = "numero_serie", length = 60)
    private String numeroSerie;

    /** Dónde está dentro del local (p. ej. "Cámara de carnes", "Sala de espera, 2.° piso"). */
    @Column(length = 100)
    private String ubicacion;

    @Column(name = "fecha_instalacion", nullable = false)
    private LocalDate fechaInstalacion;

    @Column(name = "meses_garantia", nullable = false)
    private Integer mesesGarantia = 12;

    /** Cada cuántos meses se le da mantenimiento (null = sin plan propio). */
    @Column(name = "frecuencia_meses")
    private Integer frecuenciaMeses;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Estado estado = Estado.ACTIVO;

    /** Venta con la que se vendió (null si se instaló antes del sistema o lo vendió otro). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venta_id")
    private Venta venta;

    /** Orden de instalación que lo registró (null si se registró a mano). */
    @Column(name = "orden_trabajo_id")
    private Long ordenTrabajoId;

    @Column(length = 300)
    private String notas;

    public enum Estado {
        ACTIVO, RETIRADO
    }

    public LocalDate garantiaHasta() {
        return fechaInstalacion.plusMonths(mesesGarantia);
    }

    public boolean enGarantia() {
        return !LocalDate.now().isAfter(garantiaHasta());
    }
}
