package com.bomerp.cobranza.devolucion.entity;

import com.bomerp.clientes.general.integration.Cliente;
import com.bomerp.cobranza.cobro.entity.Cobro;
import com.bomerp.ventas.venta.entity.Venta;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Dinero que JD debe devolver porque se anuló una venta que el cliente ya había pagado
 * (al contado o con abonos). Nace PENDIENTE al anular y pasa a DEVUELTA cuando se registra
 * cómo y cuándo se devolvió. Una por venta (tabla creada por migracion_devoluciones.sql).
 */
@Entity
@Table(name = "DEVOLUCIONES")
@Getter
@Setter
@NoArgsConstructor
public class Devolucion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venta_id", nullable = false, unique = true)
    private Venta venta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Venta.Moneda moneda;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Estado estado = Estado.PENDIENTE;

    @Column(name = "fecha_anulacion", nullable = false)
    private LocalDate fechaAnulacion;

    @Column(name = "fecha_devolucion")
    private LocalDate fechaDevolucion;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_pago", length = 20)
    private Cobro.MetodoPago metodoPago;

    /** N° de operación, de cheque o voucher (opcional). */
    @Column(length = 100)
    private String referencia;

    @Column(length = 300)
    private String notas;

    public enum Estado {
        PENDIENTE, DEVUELTA
    }
}
