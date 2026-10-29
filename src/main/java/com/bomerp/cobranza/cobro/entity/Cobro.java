package com.bomerp.cobranza.cobro.entity;

import com.bomerp.clientes.general.integration.Cliente;
import com.bomerp.ventas.venta.entity.Venta;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Pago recibido de un cliente (cabecera). Un mismo pago puede abonar a una o varias
 * ventas a crédito del cliente, en la misma moneda: el reparto vive en DetalleCobro.
 */
@Entity
@Table(name = "COBROS")
@Getter
@Setter
@NoArgsConstructor
public class Cobro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @Column(name = "fecha_cobro", nullable = false)
    private LocalDate fecha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Venta.Moneda moneda;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_pago", nullable = false, length = 20)
    private MetodoPago metodoPago;

    /** N° de operación, de cheque o voucher (opcional). */
    @Column(length = 100)
    private String referencia;

    @Column(length = 300)
    private String notas;

    @Column(name = "monto_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoTotal;

    /** VIGENTE cuenta en el saldo de sus ventas; ANULADO queda solo como registro. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Estado estado = Estado.VIGENTE;

    /** Usuario (preferred_username de Keycloak) que registró el cobro. */
    @Column(name = "registrado_por", length = 100)
    private String registradoPor;

    @Column(name = "fecha_anulacion")
    private LocalDate fechaAnulacion;

    @Column(name = "motivo_anulacion", length = 300)
    private String motivoAnulacion;

    @Column(name = "anulado_por", length = 100)
    private String anuladoPor;

    @OneToMany(mappedBy = "cobro", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DetalleCobro> detalles = new ArrayList<>();

    public enum Estado {
        VIGENTE, ANULADO
    }

    public enum MetodoPago {
        EFECTIVO, TRANSFERENCIA, DEPOSITO, YAPE_PLIN, CHEQUE,
        /** Constancia del depósito de detracción en el Banco de la Nación (lo deposita el cliente). */
        DETRACCION,
        /** Comprobante de retención del IGV que entrega el cliente agente de retención. */
        RETENCION;

        /** Detracción y retención no son dinero que recibe JD: cubren la parte deducida de la venta. */
        public boolean esDeduccion() {
            return this == DETRACCION || this == RETENCION;
        }
    }

    public void agregarDetalle(DetalleCobro detalle) {
        detalles.add(detalle);
        detalle.setCobro(this);
    }
}
