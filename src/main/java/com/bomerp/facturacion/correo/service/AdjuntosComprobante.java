package com.bomerp.facturacion.correo.service;

import com.bomerp.facturacion.comprobante.entity.Comprobante;
import com.bomerp.notificaciones.notificacion.service.NotificadorCorreo.Adjunto;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
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

    // Espacios de nombres de UBL 2.1 (el XML que SGE-JD firma y envía a SUNAT)
    static final String UBL_INVOICE = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";
    static final String CBC = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
    static final String CAC = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
    static final String DS = "http://www.w3.org/2000/09/xmldsig#";

    /**
     * El XML guardado tiene que ser exactamente de ESTE comprobante. Se lee con un analizador XML seguro (sin DOCTYPE,
     * sin entidades ni recursos externos) sobre una copia de los bytes, que no se modifican, y respetando la codificación
     * que declara el propio XML (SGE-JD firma en ISO-8859-1). Se comprueban los campos del documento, no textos sueltos:
     * - raíz Invoice de UBL 2.1 y firma digital (ds:Signature);
     * - cbc:ID del documento (hijo directo de la raíz) = serie y número;
     * - cbc:InvoiceTypeCode = 01 (factura) o 03 (boleta), según el tipo;
     * - RUC del emisor (cac:AccountingSupplierParty) = el de la empresa;
     * - fecha de emisión, moneda e importe total (cac:LegalMonetaryTotal/cbc:PayableAmount) = los del comprobante.
     *
     * @return el problema, o null si corresponde
     */
    public static String problemaConXml(byte[] xml, String ruc, Comprobante c) {
        String numero = c.getSerie() + "-" + c.getNumero();
        if (xml == null || xml.length == 0) {
            return numero + " no tiene XML firmado guardado: no se puede enviar sin él.";
        }
        Element raiz;
        try {
            Document doc = analizador().parse(new ByteArrayInputStream(xml));
            raiz = doc.getDocumentElement();
        } catch (Exception e) {
            return "El XML guardado de " + numero + " no se pudo leer como un documento válido (" + e.getMessage()
                    + "): no se envía.";
        }
        String noCorresponde = "El XML guardado no corresponde a " + numero + " (%s): no se envía para no mandar un archivo equivocado.";
        if (!UBL_INVOICE.equals(raiz.getNamespaceURI()) || !"Invoice".equals(raiz.getLocalName())) {
            return String.format(noCorresponde, "no es una factura o boleta UBL");
        }
        String id = texto(hijo(raiz, CBC, "ID"));
        String corto = c.getSerie() + "-" + Integer.parseInt(c.getNumero());
        if (!corto.equals(id) && !numero.equals(id)) {
            return String.format(noCorresponde, "su número es " + id);
        }
        String tipoEsperado = c.getTipo() == Comprobante.TipoComprobante.FACTURA ? "01" : "03";
        String tipo = texto(hijo(raiz, CBC, "InvoiceTypeCode"));
        if (!tipoEsperado.equals(tipo)) {
            return String.format(noCorresponde, "su tipo es " + tipo + " y el comprobante es " + tipoEsperado);
        }
        String rucEmisor = texto(hijo(hijo(hijo(hijo(raiz, CAC, "AccountingSupplierParty"), CAC, "Party"),
                CAC, "PartyIdentification"), CBC, "ID"));
        if (ruc != null && !ruc.equals(rucEmisor)) {
            return String.format(noCorresponde, "el RUC del emisor es " + rucEmisor + ", no " + ruc);
        }
        if (c.getFechaEmision() != null && !c.getFechaEmision().toString().equals(texto(hijo(raiz, CBC, "IssueDate")))) {
            return String.format(noCorresponde, "su fecha de emisión es " + texto(hijo(raiz, CBC, "IssueDate")));
        }
        if (c.getVenta() != null && c.getVenta().getMoneda() != null) {
            String moneda = "DOLARES".equals(c.getVenta().getMoneda().name()) ? "USD" : "PEN";
            String monedaXml = texto(hijo(raiz, CBC, "DocumentCurrencyCode"));
            if (!moneda.equals(monedaXml)) {
                return String.format(noCorresponde, "su moneda es " + monedaXml);
            }
        }
        if (c.getTotal() != null) {
            String total = texto(hijo(hijo(raiz, CAC, "LegalMonetaryTotal"), CBC, "PayableAmount"));
            try {
                if (total == null || new BigDecimal(total).compareTo(c.getTotal()) != 0) {
                    return String.format(noCorresponde, "su importe total es " + total + ", no " + c.getTotal());
                }
            } catch (NumberFormatException e) {
                return String.format(noCorresponde, "su importe total no es un número");
            }
        }
        if (raiz.getElementsByTagNameNS(DS, "Signature").getLength() == 0) {
            return "El XML guardado de " + numero + " no tiene firma digital: no se envía.";
        }
        return null;
    }

    /** Analizador seguro: sin DOCTYPE (bloquea XXE y entidades), sin DTD ni esquemas externos, sin XInclude. */
    private static DocumentBuilder analizador() throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        f.setXIncludeAware(false);
        f.setExpandEntityReferences(false);
        DocumentBuilder b = f.newDocumentBuilder();
        b.setErrorHandler(null);   // los errores llegan como excepción, sin imprimirse en la consola
        return b;
    }

    /** Primer hijo DIRECTO con ese nombre (no busca más abajo: el cbc:ID del documento no es el de una línea). */
    private static Element hijo(Element padre, String ns, String nombre) {
        if (padre == null) return null;
        for (Node n = padre.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && ns.equals(e.getNamespaceURI()) && nombre.equals(e.getLocalName())) {
                return e;
            }
        }
        return null;
    }

    private static String texto(Element e) {
        return e == null ? null : e.getTextContent().trim();
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
