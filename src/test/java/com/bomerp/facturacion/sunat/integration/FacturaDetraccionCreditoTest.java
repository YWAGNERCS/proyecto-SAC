package com.bomerp.facturacion.sunat.integration;

import com.bomerp.catalogo.producto.entity.ClasificacionDetraccion;
import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.clientes.general.integration.Cliente;
import com.bomerp.config.empresa.EmpresaProperties;
import com.bomerp.facturacion.comprobante.entity.Comprobante;
import com.bomerp.facturacion.pdf.service.GeneradorPdfFacturacionService;
import com.bomerp.ventas.detalleventa.entity.DetalleVenta;
import com.bomerp.ventas.tributos.DeduccionTributariaService;
import com.bomerp.ventas.tributos.TributosProperties;
import com.bomerp.ventas.venta.entity.Venta;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Prueba real de generacion PDF + XML firmado, sin Oracle, credenciales ni conexion a SUNAT. */
class FacturaDetraccionCreditoTest {
    @TempDir static Path temporal;
    private static CertificadoSunat certificado;
    private static final EmpresaProperties EMPRESA = new EmpresaProperties("JD REFRIGERACIÓN - DEMO", "20000000001",
            "Dirección de demostración, Lima", "CUENTA-NUEVA-SERVIDOR", null, null, null,
            "BCP-DEMO", "CCI-DEMO", "NACION-GENERAL-DEMO");

    @BeforeAll
    static void certificadoExclusivoDePrueba() throws Exception {
        Path keytool = Path.of(System.getProperty("java.home"), "bin", "keytool.exe");
        if (!Files.exists(keytool)) keytool = Path.of(System.getProperty("java.home"), "bin", "keytool");
        Path almacen = temporal.resolve("solo-prueba.p12");
        Path log = temporal.resolve("keytool.log");
        Process proceso = new ProcessBuilder(keytool.toString(), "-genkeypair", "-alias", "demo", "-keyalg", "RSA",
                "-keysize", "2048", "-dname", "CN=SGE-JD PRUEBA SIN VALIDEZ", "-validity", "2", "-storetype", "PKCS12",
                "-keystore", almacen.toString(), "-storepass", "test-only-changeit", "-noprompt")
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        assertThat(proceso.waitFor(30, TimeUnit.SECONDS)).isTrue();
        assertThat(proceso.exitValue()).as(new String(Files.readAllBytes(log), java.nio.charset.StandardCharsets.ISO_8859_1)).isZero();
        certificado = CertificadoSunat.cargar(almacen.toString(), "test-only-changeit");
    }

    private static Comprobante factura(String moneda, String clase, String pago) {
        Cliente cliente = new Cliente(); cliente.setTipo(Cliente.TipoCliente.EMPRESA);
        cliente.setNombreORazonSocial("CLIENTE DE DEMOSTRACIÓN SAC"); cliente.setRuc("20100066603");
        cliente.setDireccion("Dirección fiscal de prueba, Lima");
        Producto servicio = new Producto(); servicio.setNombre("Servicio de demostración");
        servicio.setSector(Producto.Sector.SERVICIO); servicio.setMoneda(Producto.Moneda.valueOf(moneda));
        servicio.setClasificacionDetraccion(ClasificacionDetraccion.valueOf(clase));
        Venta venta = new Venta(); venta.setCliente(cliente); venta.setMoneda(Venta.Moneda.valueOf(moneda));
        venta.setTipoCambio(new BigDecimal("3.75")); venta.setFechaVenta(LocalDate.of(2026,11,26));
        venta.setTipoPago(Venta.TipoPago.valueOf(pago));
        if (venta.getTipoPago() == Venta.TipoPago.CREDITO) {
            venta.setDiasCredito(15); venta.setFechaLimitePago(LocalDate.of(2026,12,11));
        }
        venta.agregarDetalle(new DetalleVenta(servicio,1,new BigDecimal("1000")));
        new DeduccionTributariaService(new TributosProperties(new BigDecimal("12"),new BigDecimal("3"),new BigDecimal("700")))
                .aplicar(venta);
        Comprobante c = new Comprobante(); c.setVenta(venta); c.setTipo(Comprobante.TipoComprobante.FACTURA);
        c.setFechaEmision(venta.getFechaVenta()); c.setSerie("F001"); c.setNumero("00000001"); c.setTotal(venta.calcularTotal());
        c.fijarReceptor(cliente); c.setCuentaDetraccionesEmision("BN-DEMO-DEL-COMPROBANTE"); c.setMedioPagoDetraccion("001");
        c.registrarAmbiente(Comprobante.AmbienteEmision.SIMULADO);
        return c;
    }

    private static String texto(byte[] pdf) throws Exception {
        try (PdfReader r = new PdfReader(pdf)) {
            StringBuilder texto = new StringBuilder(); var extractor = new PdfTextExtractor(r);
            for (int i=1;i<=r.getNumberOfPages();i++) texto.append(extractor.getTextFromPage(i)).append(' ');
            assertThat(r.getNumberOfPages()).isEqualTo(1);
            return texto.toString().replaceAll("\\s+", " ");
        }
    }

    @ParameterizedTest
    @CsvSource({
        "SOLES,MANTENIMIENTO_REPARACION,CREDITO,020,12,142.00,1038.00,142.00,12_credito_soles",
        "SOLES,CONSTRUCCION,CREDITO,030,4,47.00,1133.00,47.00,4_credito_soles",
        "SOLES,MANTENIMIENTO_REPARACION,CONTADO,020,12,142.00,1038.00,142.00,12_contado_soles",
        "DOLARES,MANTENIMIENTO_REPARACION,CREDITO,020,12,141.60,1038.40,531.00,12_credito_dolares"
    })
    void coincideElPdfConElXmlYLaFirmaEsValida(String moneda, String clase, String pago, String codigo,
            String tasa, String deduccion, String neto, String soles, String nombre) throws Exception {
        Comprobante c = factura(moneda,clase,pago);
        byte[] pdf = new GeneradorPdfFacturacionService(EMPRESA).generarFacturaPdfBytes(c);
        Files.write(Path.of("target/factura_detraccion_"+nombre+".pdf"),pdf);
        String texto = texto(pdf);
        assertThat(texto).contains("SIN VALIDEZ TRIBUTARIA", "Código de bien/servicio: " + codigo,
                "Porcentaje: " + tasa + " %", "BN-DEMO-DEL-COMPROBANTE", "001 - Depósito en cuenta", neto, "Monto a depositar en soles: S/ " + soles);
        assertThat(texto).doesNotContain("CUENTA-NUEVA-SERVIDOR", "NACION-GENERAL-DEMO");
        if (pago.equals("CREDITO")) assertThat(texto).contains("Crédito", "15 días", "11/12/2026", "Cuota 001");
        else assertThat(texto).contains("Forma de pago: Contado").doesNotContain("Cuota 001");

        byte[] xml = new XmlComprobante(EMPRESA).factura(c,certificado);
        var factory = DocumentBuilderFactory.newInstance(); factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        var xpath = XPathFactory.newInstance().newXPath();
        String spot = "//*[local-name()='PaymentTerms'][*[local-name()='ID']='Detraccion']";
        assertThat(xpath.evaluate(spot+"/*[local-name()='Amount']/@currencyID", doc)).isEqualTo("PEN");
        assertThat(new BigDecimal(xpath.evaluate(spot+"/*[local-name()='Amount']", doc))).isEqualByComparingTo(soles);
        assertThat(xpath.evaluate(spot+"/*[local-name()='PaymentMeansID']", doc)).isEqualTo(codigo);
        assertThat(new BigDecimal(xpath.evaluate(spot+"/*[local-name()='PaymentPercent']", doc))).isEqualByComparingTo(tasa);
        assertThat(xpath.evaluate("//*[local-name()='PaymentMeans']/*[local-name()='PayeeFinancialAccount']/*[local-name()='ID']",doc))
                .isEqualTo("BN-DEMO-DEL-COMPROBANTE");
        assertThat(xpath.evaluate("//*[local-name()='InvoiceTypeCode']/@listID", doc)).isEqualTo("1001");
        if (pago.equals("CREDITO")) {
            String credito = "//*[local-name()='PaymentTerms'][*[local-name()='PaymentMeansID']='Credito']";
            assertThat(new BigDecimal(xpath.evaluate(credito+"/*[local-name()='Amount']", doc))).isEqualByComparingTo(neto);
            assertThat(xpath.evaluate("//*[local-name()='PaymentDueDate']",doc)).isEqualTo("2026-12-11");
        }
        assertThat(c.getVenta().getMontoDeduccion()).isEqualByComparingTo(deduccion);
        assertThat(c.getVenta().calcularNetoACobrar()).isEqualByComparingTo(neto);
        var firma = doc.getElementsByTagNameNS(XMLSignature.XMLNS,"Signature").item(0);
        var contexto = new DOMValidateContext(certificado.certificado().getPublicKey(),firma);
        // Compatibilidad con la firma existente de xbuilder; solo en este contexto de verificacion.
        contexto.setProperty("org.jcp.xml.dsig.secureValidation", false);
        assertThat(XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(contexto).validate(contexto)).isTrue();
        Files.write(Path.of("target/factura_detraccion_"+nombre+".pdf"),pdf);
        Files.write(Path.of("target/factura_detraccion_"+nombre+".xml"),xml);
    }

    @Test
    void reproduceLaFacturaModeloDeJDConLaCuotaYElXmlFirmado() throws Exception {
        Comprobante c = factura("SOLES", "OTROS_SERVICIOS_EMPRESARIALES", "CREDITO");
        Venta v = c.getVenta();
        v.getDetalles().clear();
        Producto servicio = new Producto(); servicio.setSector(Producto.Sector.SERVICIO);
        servicio.setClasificacionDetraccion(ClasificacionDetraccion.OTROS_SERVICIOS_EMPRESARIALES);
        servicio.setNombre("Suministro y cambio de componentes - ejemplo de prueba");
        v.agregarDetalle(new DetalleVenta(servicio, 1, new BigDecimal("120")));
        v.agregarDetalle(new DetalleVenta(servicio, 1, new BigDecimal("600")));
        v.setFechaVenta(LocalDate.of(2026,11,25));v.setDiasCredito(61);v.setFechaLimitePago(LocalDate.of(2027,1,25));
        new DeduccionTributariaService(new TributosProperties(new BigDecimal("12"),new BigDecimal("3"),new BigDecimal("700"))).aplicar(v);
        c.setTotal(v.calcularTotal());c.setFechaEmision(v.getFechaVenta());c.setCuentaDetraccionesEmision("00024016395");c.setMedioPagoDetraccion("003");
        assertThat(v.calcularSubtotal()).isEqualByComparingTo("720.00");assertThat(v.calcularIgv()).isEqualByComparingTo("129.60");
        assertThat(v.calcularTotal()).isEqualByComparingTo("849.60");assertThat(v.getMontoDeduccion()).isEqualByComparingTo("102.00");
        assertThat(v.detraccionEnSoles()).isEqualByComparingTo("102.00");assertThat(v.calcularNetoACobrar()).isEqualByComparingTo("747.60");
        byte[] pdf = new GeneradorPdfFacturacionService(EMPRESA).generarFacturaPdfBytes(c);
        assertThat(texto(pdf)).contains("022", "003 - Transferencia de fondos", "00024016395", "102.00", "747.60", "25/01/2027", "SIN VALIDEZ TRIBUTARIA");
        byte[] xml = new XmlComprobante(EMPRESA).factura(c,certificado);
        var f = DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        var doc=f.newDocumentBuilder().parse(new ByteArrayInputStream(xml));var xp=XPathFactory.newInstance().newXPath();
        String spot="//*[local-name()='PaymentTerms'][*[local-name()='ID']='Detraccion']";
        String credito="//*[local-name()='PaymentTerms'][*[local-name()='PaymentMeansID']='Credito']";
        assertThat(new BigDecimal(xp.evaluate(spot+"/*[local-name()='Amount']",doc))).isEqualByComparingTo("102.00");
        assertThat(xp.evaluate(spot+"/*[local-name()='PaymentMeansID']",doc)).isEqualTo("022");
        assertThat(xp.evaluate("//*[local-name()='PaymentMeans']/*[local-name()='PaymentMeansCode']",doc)).isEqualTo("003");
        assertThat(new BigDecimal(xp.evaluate(credito+"/*[local-name()='Amount']",doc))).isEqualByComparingTo("747.60");
        assertThat(xp.evaluate("//*[local-name()='PaymentDueDate']",doc)).isEqualTo("2027-01-25");
        var contexto=new DOMValidateContext(certificado.certificado().getPublicKey(),doc.getElementsByTagNameNS(XMLSignature.XMLNS,"Signature").item(0));contexto.setProperty("org.jcp.xml.dsig.secureValidation",false);
        assertThat(XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(contexto).validate(contexto)).isTrue();
        Files.write(Path.of("target/factura_modelo_jd_prueba.pdf"),pdf);
    }

    @Test
    void conservaElImporteEnteroDeUnaVentaHistorica() throws Exception {
        Comprobante c = factura("SOLES","MANTENIMIENTO_REPARACION","CREDITO");
        c.getVenta().setMontoDetraccionSoles(null); c.getVenta().setMontoDeduccion(new BigDecimal("142.00"));
        String pdf=texto(new GeneradorPdfFacturacionService(EMPRESA).generarFacturaPdfBytes(c));
        assertThat(pdf).contains("142.00","1038.00");
        assertThat(c.getVenta().getMontoDeduccion()).isEqualByComparingTo("142.00");
    }

    @Test
    void noPresentaUnaVentaACreditoComoContadoSiFaltaElVencimiento() {
        Comprobante c = factura("SOLES","MANTENIMIENTO_REPARACION","CREDITO");
        c.getVenta().setFechaLimitePago(null);
        assertThatThrownBy(() -> new XmlComprobante(EMPRESA).factura(c,certificado)).hasMessageContaining("vencimiento");
    }
}
