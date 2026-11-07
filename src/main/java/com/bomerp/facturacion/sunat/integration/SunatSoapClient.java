package com.bomerp.facturacion.sunat.integration;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Cliente del servicio billService de SUNAT (método sendBill): un único mensaje SOAP con el XML
 * firmado dentro de un ZIP, autenticado con el usuario SOL (WS-Security UsernameToken).
 * SUNAT responde con el CDR (constancia de recepción) también en un ZIP, o con un Fault.
 */
class SunatSoapClient {

    private static final Duration ESPERA = Duration.ofSeconds(30);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String url;
    private final String usuario;
    private final String clave;

    SunatSoapClient(String url, String usuario, String clave) {
        this.url = url;
        this.usuario = usuario;
        this.clave = clave;
    }

    /** Respuesta de SUNAT: código 0 = aceptado; 2000-3999 = rechazado; 4000+ = aceptado con observaciones. */
    /** Respuesta de SUNAT: código, descripción, observaciones (notas del CDR) y el CDR en ZIP (si lo hubo). */
    record Cdr(int codigo, String descripcion, String observaciones, byte[] zipCdr) {
    }

    Cdr sendBill(String nombreArchivo, byte[] xmlFirmado) {
        String zip = Base64.getEncoder().encodeToString(comprimir(nombreArchivo + ".xml", xmlFirmado));
        String sobre = """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" \
                xmlns:ser="http://service.sunat.gob.pe" \
                xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
                <soapenv:Header><wsse:Security><wsse:UsernameToken>\
                <wsse:Username>%s</wsse:Username><wsse:Password>%s</wsse:Password>\
                </wsse:UsernameToken></wsse:Security></soapenv:Header>
                <soapenv:Body><ser:sendBill><fileName>%s.zip</fileName><contentFile>%s</contentFile></ser:sendBill></soapenv:Body>
                </soapenv:Envelope>""".formatted(escapar(usuario), escapar(clave), nombreArchivo, zip);

        HttpResponse<String> r;
        try {
            r = http.send(HttpRequest.newBuilder(URI.create(url))
                            .timeout(ESPERA)
                            .header("Content-Type", "text/xml; charset=utf-8")
                            .header("SOAPAction", "urn:sendBill")
                            .POST(HttpRequest.BodyPublishers.ofString(sobre, StandardCharsets.UTF_8))
                            .build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SunatNoDisponibleException("Envío a SUNAT interrumpido", e);
        } catch (Exception e) {
            throw new SunatNoDisponibleException("SUNAT no respondió: " + e.getMessage(), e);
        }

        if (r.statusCode() >= 500 && !r.body().contains("Envelope")) {
            throw new SunatNoDisponibleException("SUNAT respondió HTTP " + r.statusCode() + ": " + resumen(r.body()), null);
        }
        Document respuesta;
        try {
            respuesta = parsear(r.body());
        } catch (SunatNoDisponibleException e) {
            throw new SunatNoDisponibleException("SUNAT respondió algo que no es XML (HTTP " + r.statusCode() + "): " + resumen(r.body()), e);
        }
        String cdrBase64 = texto(respuesta, "applicationResponse");
        if (cdrBase64 != null) {
            byte[] zipCdr = Base64.getDecoder().decode(cdrBase64.trim());
            return leerCdr(zipCdr);
        }
        String codigoFalla = texto(respuesta, "faultcode");
        if (codigoFalla != null) {
            // Fault: el código va en <faultcode> ("soap-env:Client.2800") y el texto en <faultstring>
            // (que trae otros números, p. ej. el ticket: no sirve para sacar el código).
            String texto = texto(respuesta, "faultstring");
            return new Cdr(codigoDeFalla(codigoFalla), texto != null ? texto.trim() : codigoFalla, null, null);
        }
        throw new SunatNoDisponibleException("Respuesta inesperada de SUNAT (HTTP " + r.statusCode() + ")", null);
    }

    private static Cdr leerCdr(byte[] zipCdr) {
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(zipCdr))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (e.getName().toLowerCase().endsWith(".xml")) {
                    Document cdr = parsear(new String(zin.readAllBytes(), StandardCharsets.UTF_8));
                    String codigo = texto(cdr, "ResponseCode");
                    String descripcion = texto(cdr, "Description");
                    NodeList notas = cdr.getElementsByTagNameNS("*", "Note");
                    StringBuilder obs = new StringBuilder();
                    for (int i = 0; i < notas.getLength(); i++) {
                        if (!obs.isEmpty()) obs.append(" | ");
                        obs.append(notas.item(i).getTextContent().trim());
                    }
                    return new Cdr(codigoNumerico(codigo), descripcion, obs.isEmpty() ? null : obs.toString(), zipCdr);
                }
            }
        } catch (Exception ex) {
            throw new SunatNoDisponibleException("No se pudo leer el CDR de SUNAT: " + ex.getMessage(), ex);
        }
        throw new SunatNoDisponibleException("El CDR de SUNAT no trae XML", null);
    }

    private static byte[] comprimir(String nombre, byte[] contenido) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(nombre));
            zip.write(contenido);
            zip.closeEntry();
            zip.finish();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo comprimir el comprobante", e);
        }
    }

    private static Document parsear(String xml) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return f.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new SunatNoDisponibleException("SUNAT devolvió una respuesta que no es XML", e);
        }
    }

    private static String texto(Document d, String etiqueta) {
        NodeList n = d.getElementsByTagNameNS("*", etiqueta);
        if (n.getLength() == 0) n = d.getElementsByTagName(etiqueta);
        return n.getLength() == 0 ? null : n.item(0).getTextContent();
    }

    /** "soap-env:Client.2800" → 2800; "soap-env:Server" (sin número) → -1, que se trata como reintentable. */
    static int codigoDeFalla(String faultcode) {
        int punto = faultcode.lastIndexOf('.');
        String numero = punto >= 0 ? faultcode.substring(punto + 1).trim() : "";
        return numero.matches("\\d{1,4}") ? Integer.parseInt(numero) : -1;
    }

    private static int codigoNumerico(String s) {
        if (s == null) return -1;
        String digitos = s.replaceAll("\\D", "");
        return digitos.isEmpty() ? -1 : Integer.parseInt(digitos.length() > 4 ? digitos.substring(digitos.length() - 4) : digitos);
    }

    /** Primeros caracteres legibles de una respuesta que no es SOAP (p. ej. una página de error HTML). */
    private static String resumen(String cuerpo) {
        String t = cuerpo.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        return t.length() > 160 ? t.substring(0, 160) + "…" : t;
    }

    private static String escapar(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** SUNAT caída, lenta o con una respuesta ilegible: el comprobante queda PENDIENTE para reintentar. */
    static class SunatNoDisponibleException extends RuntimeException {
        SunatNoDisponibleException(String mensaje, Throwable causa) {
            super(mensaje, causa);
        }
    }
}
