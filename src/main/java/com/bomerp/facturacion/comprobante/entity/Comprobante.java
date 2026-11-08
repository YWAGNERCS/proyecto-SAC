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
// Un comprobante no reemplazado por venta: lo asegura el índice por función UX_COMPROBANTE_VIGENTE_VENTA
// (migracion_comprobante_reemplazo.sql), que JPA no sabe declarar.
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

    /**
     * Venta que originó este comprobante. Una venta puede tener más de uno: si SUNAT rechaza el comprobante
     * con CDR, su número queda usado y se emite otro para la misma venta (comprobante de reemplazo). Solo uno
     * por venta puede estar sin reemplazar (índice UX_COMPROBANTE_VIGENTE_VENTA).
     */
    @ManyToOne
    @JoinColumn(name = "venta_id", nullable = false)
    private Venta venta;

    /** Rechazado por SUNAT y sustituido por otro comprobante de la misma venta: queda solo como historial. */
    @Column(nullable = false)
    private boolean reemplazado = false;

    /** El comprobante rechazado al que este reemplaza (null si es el primero de la venta). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reemplaza_a_id")
    private Comprobante reemplazaA;

    /*
     * Datos del receptor tal como se emitió el comprobante. Editar el cliente después no cambia el XML ni el
     * PDF de un comprobante ya emitido. Tipo de documento según el catálogo 06 de SUNAT: 6 RUC, 1 DNI, 0 sin documento.
     */
    @Column(name = "receptor_tipo_doc", length = 1)
    private String receptorTipoDoc;

    @Column(name = "receptor_num_doc", length = 15)
    private String receptorNumDoc;

    @Column(name = "receptor_nombre", length = 150)
    private String receptorNombre;

    @Column(name = "receptor_direccion", length = 200)
    private String receptorDireccion;

    /** Copia los datos actuales del cliente como receptor del comprobante (al emitirlo o al corregir una excepción). */
    public void fijarReceptor(com.bomerp.clientes.general.integration.Cliente cliente) {
        if (cliente.getRuc() != null && !cliente.getRuc().isBlank()) {
            receptorTipoDoc = "6";
            receptorNumDoc = cliente.getRuc();
        } else if (cliente.getDni() != null && !cliente.getDni().isBlank()) {
            receptorTipoDoc = "1";
            receptorNumDoc = cliente.getDni();
        } else {
            receptorTipoDoc = "0";
            receptorNumDoc = "-";
        }
        receptorNombre = cliente.getNombreORazonSocial();
        receptorDireccion = cliente.getDireccion();
    }

    /**
     * SUNAT lo rechazó con constancia (CDR, códigos 2000-3999) o informó que ese número ya figura rechazado o
     * anulado (1032): el número quedó usado y no puede reenviarse; la venta necesita un comprobante de reemplazo.
     * Las demás respuestas que hoy se guardan como RECHAZADO (0200-1999 salvo 1032) son excepciones: SUNAT no
     * registró el comprobante, así que se corrige el dato y se reenvía con el mismo número.
     */
    public boolean rechazoDefinitivo() {
        Integer codigo = getEnvio().getCodigo();
        return estadoSunat == EstadoSunat.RECHAZADO && codigo != null
                && ((codigo >= 2000 && codigo <= 3999) || codigo == CODIGO_YA_RECHAZADO);
    }

    /** SUNAT: el comprobante ya está informado y figura anulado o rechazado. */
    public static final int CODIGO_YA_RECHAZADO = 1032;

    public enum TipoComprobante {
        FACTURA, BOLETA, NOTA_CREDITO
    }

    /**
     * PENDIENTE: aún no enviado o SUNAT no respondió (se reintenta solo). ACEPTADO: válido.
     * OBSERVADO: válido, con observaciones de SUNAT. RECHAZADO: sin validez; si fue un rechazo definitivo
     * (ver rechazoDefinitivo) se emite un comprobante de reemplazo, si no, se corrige y se reenvía.
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
