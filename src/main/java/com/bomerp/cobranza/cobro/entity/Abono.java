package com.bomerp.cobranza.cobro.entity;

import com.bomerp.ventas.venta.entity.Venta;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * El abono de un cobro a una venta concreta (el término del negocio, glosario de ADS S6).
 *
 * ADR-007 (ADS S14): antes se llamaba DetalleCobro y lo creaba CobranzaService. Ahora solo lo crea su cobro, con
 * {@link Cobro#abonar(Venta, BigDecimal)}: por eso no tiene setters ni un constructor público. La tabla sigue
 * siendo DETALLE_COBRO.
 */
@Entity
@Table(name = "DETALLE_COBRO")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // JPA
public class Abono {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cobro_id", nullable = false)
    private Cobro cobro;

    // Brecha B1 de ADS S8: guardar solo el ventaId (otro módulo). Queda pendiente; el ADR-007 no la resuelve.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venta_id", nullable = false)
    private Venta venta;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    Abono(Cobro cobro, Venta venta, BigDecimal monto) {
        this.cobro = cobro;
        this.venta = venta;
        this.monto = monto;
    }
}
