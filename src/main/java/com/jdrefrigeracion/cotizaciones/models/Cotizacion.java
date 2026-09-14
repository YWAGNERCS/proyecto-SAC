package com.jdrefrigeracion.cotizaciones.models;
import com.jdrefrigeracion.compras.models.Compra;
import com.jdrefrigeracion.ventas.models.DetalleVenta;
import com.jdrefrigeracion.compras.models.DetalleCompra;
import com.jdrefrigeracion.ventas.models.Venta;

import com.jdrefrigeracion.clientes.integrations.Cliente;
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
 * Cabecera de una cotización. El detalle (productos/servicios cotizados)
 * vive en ItemCotizacion — mismo patrón cabecera-detalle que Venta/DetalleVenta
 * y Compra/DetalleCompra, tal como pidió el equipo docente.
 */
@Entity
@Table(name = "COTIZACIONES")
@Getter
@Setter
@NoArgsConstructor
public class Cotizacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "cliente_id", nullable = false)
    @NotNull
    private Cliente cliente;

    @Column(name = "fecha_emision", nullable = false)
    private LocalDate fechaEmision = LocalDate.now();

    /** RNF confirmado con Lisandro: una cotización vence si no hay respuesta */
    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoCotizacion estado = EstadoCotizacion.PENDIENTE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Moneda moneda;

    @OneToMany(mappedBy = "cotizacion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemCotizacion> items = new ArrayList<>();

    /** Canal por el que se envió: WHATSAPP, CORREO, o ambos (se registran 2 filas si aplica) */
    @Column(name = "enviado_whatsapp")
    private boolean enviadoWhatsApp;

    @Column(name = "enviado_correo")
    private boolean enviadoCorreo;

    public enum EstadoCotizacion {
        PENDIENTE, ACEPTADA, RECHAZADA, VENCIDA
    }

    public enum Moneda {
        SOLES, DOLARES
    }

    /** Suma de todos los items, antes de IGV */
    public BigDecimal calcularSubtotal() {
        return items.stream()
                .map(ItemCotizacion::calcularSubtotalItem)
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

    public void agregarItem(ItemCotizacion item) {
        items.add(item);
        item.setCotizacion(this);
    }
}
