package com.bomerp.facturacion.correo.service;

import com.bomerp.facturacion.comprobante.entity.Comprobante;
import com.bomerp.notificaciones.notificacion.service.NotificadorCorreo.Adjunto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/**
 * Adjuntos del correo de un comprobante: el PDF (representación impresa) y el XML firmado exacto que está guardado en
 * Oracle (los bytes que se enviaron a SUNAT, sin volver a armarlo ni reserializarlo). Se nombran como SUNAT nombra el
 * archivo: RUC-TIPO-SERIE-NÚMERO (20521632268-01-F001-125.xml).
 */
public final class AdjuntosComprobante {

    private AdjuntosComprobante() {}

    /** "20521632268-01-F001-125": el mismo nombre con que se envió el XML a SUNAT. */
    public static String nombreBase(String ruc, Comprobante c) {
        String tipo = c.getTipo() == Comprobante.TipoComprobante.FACTURA ? "01" : "03";
        return ruc + "-" + tipo + "-" + c.getSerie() + "-" + Integer.parseInt(c.getNumero());
    }

    /**
     * El XML guardado tiene que ser de ESTE comprobante: su número (cbc:ID, con o sin ceros) y el RUC del emisor.
     *
     * @return el problema, o null si corresponde
     */
    public static String problemaConXml(byte[] xml, String ruc, Comprobante c) {
        String numero = c.getSerie() + "-" + c.getNumero();
        if (xml == null || xml.length == 0) {
            return numero + " no tiene XML firmado guardado: no se puede enviar sin él.";
        }
        String texto = new String(xml, StandardCharsets.UTF_8);
        String corto = c.getSerie() + "-" + Integer.parseInt(c.getNumero());
        boolean mismoNumero = texto.contains("<cbc:ID>" + corto + "</cbc:ID>") || texto.contains("<cbc:ID>" + numero + "</cbc:ID>");
        if (!mismoNumero) {
            return "El XML guardado no corresponde a " + numero + ": no se envía para no mandar un archivo equivocado.";
        }
        if (ruc != null && !texto.contains(ruc)) {
            return "El XML guardado de " + numero + " no tiene el RUC de la empresa (" + ruc + "): no se envía.";
        }
        return null;
    }

    public static List<Adjunto> armar(String ruc, Comprobante c, byte[] pdf, byte[] xmlFirmado) {
        String base = nombreBase(ruc, c);
        return List.of(new Adjunto(base + ".pdf", pdf, "application/pdf"),
                new Adjunto(base + ".xml", xmlFirmado, "application/xml"));
    }

    /** "20521632268-01-F001-125.pdf (35 KB, sha256 1a2b3c4d…)" de cada adjunto, para el historial. */
    public static String resumen(List<Adjunto> adjuntos) {
        StringBuilder sb = new StringBuilder();
        for (Adjunto a : adjuntos) {
            if (!sb.isEmpty()) sb.append("; ");
            sb.append(a.nombre()).append(" (").append(Math.max(1, Math.round(a.contenido().length / 1024.0)))
                    .append(" KB, sha256 ").append(huella(a.contenido()).substring(0, 16)).append(")");
        }
        return sb.toString();
    }

    public static String huella(byte[] contenido) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
