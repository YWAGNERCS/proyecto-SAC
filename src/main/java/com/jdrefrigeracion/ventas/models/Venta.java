package com.jdrefrigeracion.ventas.models;

import com.jdrefrigeracion.clientes.integrations.Cliente;
import com.jdrefrigeracion.cotizaciones.models.Cotizacion;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Cabecera de una venta. Puede originarse de dos formas:
 *   1) A partir de una Cotizacion aceptada (RF: conversión sin doble digitación)
 *   2) De forma directa, sin pasar por cotización previa
 *
 * El detalle (productos vendidos) vive en DetalleVenta.
 */
@Entity
@Table(name = "VENTAS")
@Getter
@Setter
@NoArgsConstructor
public class Venta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "cliente_id", nullable = false)
    @NotNull
    private Cliente cliente;

    /** Nula si la venta se registró directamente, sin cotización previa */
    @ManyToOne
    @JoinColumn(name = "cotizacion_id")
    private Cotizacion cotizacionOrigen;

    @Column(name = "fecha_venta", nullable = false)
    private LocalDate fechaVenta = LocalDate.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoPago tipoPago;

    /** Solo aplica si tipoPago = CREDITO (RNF: seguimiento de cobranza) */
    @Column(name = "dias_credito")
    private Integer diasCredito;

    @Column(name = "fecha_limite_pago")
    private LocalDate fechaLimitePago;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoPago estadoPago = EstadoPago.PENDIENTE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Moneda moneda;

    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DetalleVenta> detalles = new ArrayList<>();

    /** Referencia al comprobante generado (se conecta con el módulo Facturación) */
    @Column(name = "comprobante_id")
    private Long comprobanteId;

    public enum TipoPago {
        CONTADO, CREDITO
    }

    public enum EstadoPago {
        PENDIENTE, PAGADO, ATRASADO
    }

    public enum Moneda {
        SOLES, DOLARES
    }

    public BigDecimal calcularSubtotal() {
        return detalles.stream()
                .map(DetalleVenta::calcularSubtotalDetalle)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static final BigDecimal IGV = new BigDecimal("0.18");

    public BigDecimal calcularIgv() {
        return calcularSubtotal().multiply(IGV).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    public BigDecimal calcularTotal() {
        return calcularSubtotal().add(calcularIgv()).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    public void agregarDetalle(DetalleVenta detalle) {
        detalles.add(detalle);
        detalle.setVenta(this);
    }
}
