package com.bomerp.facturacion.comprobante.entity;

import com.bomerp.ventas.venta.entity.Venta;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
// Un mismo número no puede repetirse dentro de una serie (exigencia de SUNAT)
@Table(name = "COMPROBANTES", uniqueConstraints = @UniqueConstraint(name = "UK_COMPROBANTE_SERIE_NUMERO", columnNames = {"serie", "numero"}))
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

    /** Respuesta de SUNAT al último envío (código, mensaje, XML firmado y CDR). */
    @Embedded
    private EnvioSunat envio = new EnvioSunat();

    // Relación directa a la Venta que originó este comprobante
    @OneToOne
    @JoinColumn(name = "venta_id", nullable = false, unique = true)
    private Venta venta;

    public enum TipoComprobante {
        FACTURA, BOLETA, NOTA_CREDITO
    }

    /**
     * PENDIENTE: aún no enviado o SUNAT no respondió (se reintenta solo). ACEPTADO: válido.
     * OBSERVADO: válido, con observaciones de SUNAT. RECHAZADO: sin validez; hay que corregir y reenviar.
     */
    public enum EstadoSunat {
        PENDIENTE, ACEPTADO, OBSERVADO, RECHAZADO;

        /** El comprobante tiene validez tributaria. */
        public boolean valido() {
            return this == ACEPTADO || this == OBSERVADO;
        }
    }

    /** El comprobante sin envío guardado (datos viejos): evita nulos al leer. */
    public EnvioSunat getEnvio() {
        if (envio == null) envio = new EnvioSunat();
        return envio;
    }

    public BigDecimal getTotal() {
        return total != null ? total.setScale(2, java.math.RoundingMode.HALF_UP) : null;
    }
}
