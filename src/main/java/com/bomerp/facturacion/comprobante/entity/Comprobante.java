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

    /**
     * Ambiente de SUNAT en que se aceptó el comprobante (PRODUCCION, BETA o SIMULADO). Se fija una sola vez, al
     * aceptarlo, y no cambia aunque después se cambie la configuración: así un comprobante aceptado por la beta nunca
     * pasa por válido si el servidor se pasa a producción. Solo PRODUCCION tiene validez tributaria. Lo protege además
     * el disparador TRG_COMPROBANTE_AMBIENTE en Oracle. NO_REGISTRADO: aceptado antes de que existiera este dato.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "ambiente_emision", length = 13)
    @lombok.Setter(lombok.AccessLevel.NONE)
    private AmbienteEmision ambienteEmision;

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

    /**
     * El tipo emitido ya no corresponde a los datos actuales del cliente (informe D-11): una factura necesita el
     * RUC del receptor, y un cliente que pasó a persona natural ya no tiene RUC (ClienteService lo borra); al revés,
     * a una empresa se le emite factura, no boleta.
     */
    public boolean tipoNoCorrespondeA(com.bomerp.clientes.general.integration.Cliente cliente) {
        boolean conRuc = cliente.getRuc() != null && !cliente.getRuc().isBlank();
        boolean empresa = cliente.getTipo() == com.bomerp.clientes.general.integration.Cliente.TipoCliente.EMPRESA;
        return (tipo == TipoComprobante.FACTURA && !conRuc) || (tipo == TipoComprobante.BOLETA && empresa);
    }

    /**
     * SUNAT no lo registró (excepción) y el tipo ya no corresponde al cliente corregido: no puede reenviarse con su
     * número (saldría una factura con DNI, que SUNAT rechaza); la venta necesita un comprobante de reemplazo del tipo correcto.
     */
    public boolean necesitaOtroTipo() {
        return !reemplazado && estadoSunat == EstadoSunat.RECHAZADO && !rechazoDefinitivo()
                && venta != null && venta.getCliente() != null && tipoNoCorrespondeA(venta.getCliente());
    }

    /** SUNAT: el comprobante ya está informado y figura anulado o rechazado. */
    public static final int CODIGO_YA_RECHAZADO = 1032;

    /** SUNAT: ya tiene ese número por un envío anterior, sin decir si lo aceptó (queda por confirmar). */
    public static final int CODIGO_YA_REGISTRADO = 1033;

    /**
     * Plazo para enviar facturas y boletas a SUNAT: 3 días calendario contados desde el día siguiente a la
     * emisión (RS 003-2023/SUNAT). Lo que se envía después no tiene calidad de comprobante electrónico.
     */
    public static final int DIAS_PLAZO_ENVIO = 3;

    /** Último día en que SUNAT todavía recibe el comprobante: emitido el 7, se puede enviar hasta el 10 inclusive. */
    public LocalDate fechaLimiteEnvio() {
        return fechaEmision.plusDays(DIAS_PLAZO_ENVIO);
    }

    /**
     * SUNAT todavía no registró el comprobante, así que aún hay que hacerlo llegar: pendiente de envío (salvo
     * el 1033, que SUNAT ya tiene) o una excepción que se corrige y se reenvía con el mismo número.
     */
    public boolean faltaQueSunatLoRegistre() {
        if (reemplazado) return false;
        if (estadoSunat == EstadoSunat.PENDIENTE) {
            return getEnvio().getCodigo() == null || getEnvio().getCodigo() != CODIGO_YA_REGISTRADO;
        }
        return estadoSunat == EstadoSunat.RECHAZADO && !rechazoDefinitivo();
    }

    /**
     * Pasó el plazo sin que SUNAT lo registrara (informe de rechazos SUNAT, D-6): ya no se envía, porque lo
     * que llega tarde no tiene validez aunque SUNAT lo reciba, y la venta necesita un comprobante de reemplazo.
     */
    public boolean fueraDePlazo(LocalDate hoy) {
        return faltaQueSunatLoRegistre() && hoy.isAfter(fechaLimiteEnvio());
    }

    public enum TipoComprobante {
        FACTURA, BOLETA, NOTA_CREDITO
    }

    /** Dónde se emitió: solo PRODUCCION tiene validez tributaria. */
    public enum AmbienteEmision {
        PRODUCCION, BETA, SIMULADO, NO_REGISTRADO;

        public boolean conValidezTributaria() {
            return this == PRODUCCION;
        }
    }

    /** Registra el ambiente en que SUNAT lo aceptó, solo la primera vez: después no se puede cambiar. */
    public void registrarAmbiente(AmbienteEmision ambiente) {
        if (this.ambienteEmision == null && ambiente != null) {
            this.ambienteEmision = ambiente;
        }
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
