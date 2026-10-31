package com.bomerp.ventas.venta.entity;
import com.bomerp.ventas.detalleventa.entity.DetalleVenta;

import com.bomerp.clientes.general.integration.Cliente;
import com.bomerp.proformas.proforma.entity.Proforma;
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
 *   1) A partir de una Proforma aceptada (RF: conversión sin doble digitación)
 *   2) De forma directa, sin pasar por proforma previa
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

    /** Nula si la venta se registró directamente, sin proforma previa */
    @ManyToOne
    @JoinColumn(name = "proforma_id")
    private Proforma proformaOrigen;

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

    /**
     * Tipo de cambio usado en la venta (soles por dólar). Queda guardado para que la venta,
     * su factura y los reportes no cambien cuando el tipo de cambio sube o baja después.
     */
    @Column(name = "tipo_cambio", precision = 10, scale = 4)
    private BigDecimal tipoCambio;

    /**
     * Ciclo de vida de la venta (independiente del estado de cobro).
     * La columna se agregó con DEFAULT 'REGISTRADA' (las ventas que ya existían quedaron REGISTRADA);
     * ese DEFAULT sigue en Oracle. Las ventas nuevas toman REGISTRADA desde Java.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoVenta estado = EstadoVenta.REGISTRADA;

    /** Fecha en que se anuló (null si está REGISTRADA). La usa el kardex para la devolución de stock. */
    @Column(name = "fecha_anulacion")
    private LocalDate fechaAnulacion;

    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DetalleVenta> detalles = new ArrayList<>();

    /**
     * Quién registró la venta: el "sub" del JWT de Keycloak (UUID), nunca un dato que envíe el cliente.
     * Nulo en las ventas anteriores a S10 (no se reescribe el pasado).
     */
    @Column(name = "vendedor_id", length = 36)
    private String vendedorId;

    /** preferred_username del mismo token (lisandro, lucy), para mostrarlo sin consultar Keycloak. */
    @Column(name = "vendedor_nombre", length = 60)
    private String vendedorNombre;

    /**
     * Detracción (SPOT) o retención del IGV que corresponde a la venta, calculada al registrarla
     * (ver DeduccionTributariaService). Ese monto no lo paga el cliente a JD: lo deposita en la cuenta
     * de detracciones del Banco de la Nación o lo retiene y entrega el comprobante de retención.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_deduccion", nullable = false, length = 12)
    private TipoDeduccion tipoDeduccion = TipoDeduccion.NINGUNA;

    @Column(name = "porcentaje_deduccion", precision = 5, scale = 2)
    private BigDecimal porcentajeDeduccion;

    /** En la moneda de la venta. */
    @Column(name = "monto_deduccion", precision = 12, scale = 2)
    private BigDecimal montoDeduccion;

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

    public enum EstadoVenta {
        REGISTRADA, ANULADA
    }

    public enum TipoDeduccion {
        NINGUNA, DETRACCION, RETENCION
    }

    /** Lo que el cliente le paga directamente a JD: total menos la detracción o retención. */
    public BigDecimal calcularNetoACobrar() {
        BigDecimal deduccion = montoDeduccion != null ? montoDeduccion : BigDecimal.ZERO;
        return calcularTotal().subtract(deduccion);
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

    /** Agrega la línea y le guarda el costo del producto de hoy, llevado a la moneda de la venta. */
    public void agregarDetalle(DetalleVenta detalle) {
        detalles.add(detalle);
        detalle.setVenta(this);
        if (detalle.getCostoUnitario() == null && detalle.getProducto() != null && detalle.getProducto().getCostoBase() != null
                && moneda != null) {
            String monedaProducto = detalle.getProducto().getMoneda().name();
            if (monedaProducto.equals(moneda.name()) || tipoCambio != null) {
                detalle.setCostoUnitario(com.bomerp.config.tipocambio.TipoCambioService.convertir(
                        detalle.getProducto().getCostoBase(), monedaProducto, moneda.name(), tipoCambio)
                        .setScale(2, java.math.RoundingMode.HALF_UP));
            }
        }
    }
}
