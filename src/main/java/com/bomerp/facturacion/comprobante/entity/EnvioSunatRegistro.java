package com.bomerp.facturacion.comprobante.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Una respuesta de SUNAT a un envío de un comprobante o de una nota de crédito. Se guarda cada una, sin pisar
 * las anteriores: SUNAT exige conservar las constancias de rechazo, y el resumen de EnvioSunat solo tiene la
 * última. Solo se inserta, nunca se modifica.
 */
@Entity
@Table(name = "ENVIOS_SUNAT")
@Getter
@NoArgsConstructor
public class EnvioSunatRegistro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "comprobante_id")
    private Long comprobanteId;

    @Column(name = "nota_credito_id")
    private Long notaCreditoId;

    /** Número de intento del documento (1, 2, ...), tal como lo cuenta EnvioSunat. */
    @Column(nullable = false)
    private int intento;

    @Column(nullable = false)
    private LocalDateTime fecha;

    /** Cómo terminó: ACEPTADO, OBSERVADO, RECHAZADO, REINTENTAR (sin respuesta) o POR_CONFIRMAR (1033). */
    @Column(nullable = false, length = 15)
    private String resultado;

    private Integer codigo;

    @Column(length = 1000)
    private String mensaje;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    private byte[] xml;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    private byte[] cdr;

    /** Ambiente de SUNAT al que se envió este intento (PRODUCCION, BETA o SIMULADO); null en los anteriores. */
    @Column(length = 13)
    private String ambiente;

    /** El ambiente del intento: se fija al guardarlo y no cambia (el registro solo se inserta). */
    public EnvioSunatRegistro conAmbiente(String ambiente) {
        this.ambiente = ambiente;
        return this;
    }

    private EnvioSunatRegistro(Long comprobanteId, Long notaCreditoId, String resultado, EnvioSunat envio,
                               byte[] xml, byte[] cdr) {
        this.comprobanteId = comprobanteId;
        this.notaCreditoId = notaCreditoId;
        this.resultado = resultado;
        this.intento = envio.getIntentos();
        this.fecha = envio.getUltimoIntento() != null ? envio.getUltimoIntento() : LocalDateTime.now();
        this.codigo = envio.getCodigo();
        this.mensaje = envio.getMensaje();
        this.xml = xml;
        this.cdr = cdr;
    }

    /** El envío que acaba de registrarse en el resumen del comprobante, con el XML y el CDR de ESTE intento. */
    public static EnvioSunatRegistro deComprobante(Long comprobanteId, String resultado, EnvioSunat envio, byte[] xml, byte[] cdr) {
        return new EnvioSunatRegistro(comprobanteId, null, resultado, envio, xml, cdr);
    }

    public static EnvioSunatRegistro deNota(Long notaCreditoId, String resultado, EnvioSunat envio, byte[] xml, byte[] cdr) {
        return new EnvioSunatRegistro(null, notaCreditoId, resultado, envio, xml, cdr);
    }

    public boolean tieneXml() {
        return xml != null;
    }

    public boolean tieneCdr() {
        return cdr != null;
    }
}
