package com.bomerp.facturacion.correo;

import java.nio.charset.Charset;

/**
 * XML UBL 2.1 de prueba con la misma estructura que el que SGE-JD firma y envía a SUNAT (los mismos espacios de nombres,
 * declarado en ISO-8859-1, firma dentro de ext:UBLExtensions). Cada campo se puede cambiar para probar los rechazos.
 */
public final class XmlUblDePrueba {

    public String id = "F001-125";
    public String tipo = "01";
    public String fecha = "2026-11-08";
    public String moneda = "PEN";
    public String rucEmisor = "20521632268";
    public String rucCliente = "20999200003";
    public String total = "1180.00";
    public boolean firmado = true;
    /** Algo antes de la raíz (p. ej. un DOCTYPE malicioso). */
    public String prologo = "";
    /** Un cbc:ID más adentro (p. ej. en una línea), que un buscador de texto confundiría con el del documento. */
    public String idEnUnaLinea = "1";

    public byte[] bytes() {
        String xml = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n" + prologo
                + "<Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\""
                + " xmlns:cac=\"urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2\""
                + " xmlns:cbc=\"urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2\""
                + " xmlns:ds=\"http://www.w3.org/2000/09/xmldsig#\""
                + " xmlns:ext=\"urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2\">\n"
                + "  <ext:UBLExtensions><ext:UBLExtension><ext:ExtensionContent>"
                + (firmado ? "<ds:Signature Id=\"SGEJD\"><ds:SignatureValue>abc==</ds:SignatureValue></ds:Signature>" : "")
                + "</ext:ExtensionContent></ext:UBLExtension></ext:UBLExtensions>\n"
                + "  <cbc:UBLVersionID>2.1</cbc:UBLVersionID>\n"
                + "  <cbc:ID>" + id + "</cbc:ID>\n"
                + "  <cbc:IssueDate>" + fecha + "</cbc:IssueDate>\n"
                + "  <cbc:InvoiceTypeCode listID=\"0101\">" + tipo + "</cbc:InvoiceTypeCode>\n"
                + "  <cbc:DocumentCurrencyCode>" + moneda + "</cbc:DocumentCurrencyCode>\n"
                + "  <cac:AccountingSupplierParty><cac:Party><cac:PartyIdentification><cbc:ID schemeID=\"6\">" + rucEmisor
                + "</cbc:ID></cac:PartyIdentification><cac:PartyLegalEntity><cbc:RegistrationName>"
                + "<![CDATA[JD REFRIGERACIÓN S.A.C. - Breña]]></cbc:RegistrationName></cac:PartyLegalEntity></cac:Party>"
                + "</cac:AccountingSupplierParty>\n"
                + "  <cac:AccountingCustomerParty><cac:Party><cac:PartyIdentification><cbc:ID schemeID=\"6\">" + rucCliente
                + "</cbc:ID></cac:PartyIdentification></cac:Party></cac:AccountingCustomerParty>\n"
                + "  <cac:LegalMonetaryTotal><cbc:PayableAmount currencyID=\"" + moneda + "\">" + total
                + "</cbc:PayableAmount></cac:LegalMonetaryTotal>\n"
                + "  <cac:InvoiceLine><cbc:ID>" + idEnUnaLinea + "</cbc:ID><cac:Item><cbc:Description>"
                + "<![CDATA[Gas refrigerante R32 - ñandú]]></cbc:Description></cac:Item></cac:InvoiceLine>\n"
                + "</Invoice>\n";
        // Como el real: codificado en ISO-8859-1 (las tildes ocupan un byte, no dos como en UTF-8)
        return xml.getBytes(Charset.forName("ISO-8859-1"));
    }
}
