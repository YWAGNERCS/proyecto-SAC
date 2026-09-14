package com.jdrefrigeracion.facturacion.models;

import com.jdrefrigeracion.ventas.models.Venta;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "COMPROBANTES")
@Getter
@Setter
@NoArgsConstructor
public class Comprobante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 4)
    private String serie;

    @Column(nullable = false, length = 8)
    private String numero;

    @Column(nullable = false)
    private LocalDate fechaEmision = LocalDate.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private TipoComprobante tipo;

    @Column(nullable = false)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private EstadoSunat estadoSunat = EstadoSunat.PENDIENTE;

    @Column(length = 500)
    private String enlacePdf;

    @Column(length = 500)
    private String enlaceXml;

    // Relación directa a la Venta que originó este comprobante
    @com.fasterxml.jackson.annotation.JsonIgnore
    @OneToOne
    @JoinColumn(name = "venta_id", nullable = false, unique = true)
    private Venta venta;

    public enum TipoComprobante {
        FACTURA, BOLETA, NOTA_CREDITO
    }

    public enum EstadoSunat {
        PENDIENTE, ACEPTADO, RECHAZADO
    }

    public BigDecimal getTotal() {
        return total != null ? total.setScale(2, java.math.RoundingMode.HALF_UP) : null;
    }
}
