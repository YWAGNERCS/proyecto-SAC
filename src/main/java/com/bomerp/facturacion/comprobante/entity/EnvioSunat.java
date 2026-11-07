package com.bomerp.facturacion.comprobante.entity;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.FetchType;
import jakarta.persistence.Lob;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Lo que respondió SUNAT al último envío de un comprobante o nota de crédito. Se guarda junto al
 * documento: el XML firmado y el CDR (constancia de recepción) son el respaldo legal de la emisión.
 */
@Embeddable
@Getter
@Setter
public class EnvioSunat {

    /** Código de SUNAT: 0 aceptado, 2000-3999 rechazo, 4000+ observación. Vacío si aún no respondió. */
    @Column(name = "sunat_codigo")
    private Integer codigo;

    /** Descripción de SUNAT, motivo del rechazo u observaciones, o el último error de conexión. */
    @Column(name = "sunat_mensaje", length = 1000)
    private String mensaje;

    @Column(name = "sunat_intentos", nullable = false)
    private int intentos;

    @Column(name = "sunat_ultimo_intento")
    private LocalDateTime ultimoIntento;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "sunat_xml")
    private byte[] xmlFirmado;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "sunat_cdr")
    private byte[] cdr;

    /** Registra un intento: el mensaje se recorta al largo de la columna. */
    public void registrarIntento(Integer codigo, String mensaje, byte[] xmlFirmado, byte[] cdr) {
        this.intentos++;
        this.ultimoIntento = LocalDateTime.now();
        this.codigo = codigo;
        this.mensaje = mensaje == null ? null : (mensaje.length() > 1000 ? mensaje.substring(0, 1000) : mensaje);
        if (xmlFirmado != null) this.xmlFirmado = xmlFirmado;
        if (cdr != null) this.cdr = cdr;
    }
}
