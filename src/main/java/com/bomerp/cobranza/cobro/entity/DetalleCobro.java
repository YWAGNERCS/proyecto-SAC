package com.bomerp.cobranza.cobro.entity;

import com.bomerp.ventas.venta.entity.Venta;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Parte de un cobro que se abona a una venta concreta. */
@Entity
@Table(name = "DETALLE_COBRO")
@Getter
@Setter
@NoArgsConstructor
public class DetalleCobro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cobro_id", nullable = false)
    private Cobro cobro;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venta_id", nullable = false)
    private Venta venta;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    public DetalleCobro(Venta venta, BigDecimal monto) {
        this.venta = venta;
        this.monto = monto;
    }
}
