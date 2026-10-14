package com.bomerp.facturacion.notacredito.entity;

import com.bomerp.facturacion.comprobante.entity.Comprobante;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Nota de crédito electrónica: anula (total) un comprobante ya aceptado por SUNAT.
 * Un comprobante emitido no se borra ni se edita; se corrige emitiendo esta nota
 * que lo referencia. Serie FC01 para facturas y BC01 para boletas.
 */
@Entity
@Table(name = "NOTAS_CREDITO")
@Getter
@Setter
@NoArgsConstructor
public class NotaCredito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 4)
    private String serie;

    @Column(nullable = false, length = 8)
    private String numero;

    @Column(nullable = false)
    private LocalDate fechaEmision = LocalDate.now();

    @Column(nullable = false, length = 250)
    private String motivo;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Comprobante.EstadoSunat estadoSunat = Comprobante.EstadoSunat.PENDIENTE;

    @Column(length = 500)
    private String enlacePdf;

    @Column(length = 500)
    private String enlaceXml;

    /** Comprobante que esta nota anula (una sola nota por comprobante: anulación total). */
    @JsonIgnore
    @OneToOne
    @JoinColumn(name = "comprobante_id", nullable = false, unique = true)
    private Comprobante comprobanteReferencia;

    @JsonProperty("comprobanteAnulado")
    public String getNumeroComprobanteReferencia() {
        return comprobanteReferencia != null
                ? comprobanteReferencia.getSerie() + "-" + comprobanteReferencia.getNumero()
                : null;
    }

    @JsonProperty("ventaId")
    public Long getVentaId() {
        return comprobanteReferencia != null && comprobanteReferencia.getVenta() != null
                ? comprobanteReferencia.getVenta().getId()
                : null;
    }
}
