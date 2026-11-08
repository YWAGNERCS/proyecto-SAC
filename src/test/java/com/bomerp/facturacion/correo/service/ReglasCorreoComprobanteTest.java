package com.bomerp.facturacion.correo.service;

import com.bomerp.config.empresa.EmpresaProperties;
import com.bomerp.facturacion.comprobante.entity.Comprobante;
import com.bomerp.facturacion.comprobante.entity.Comprobante.AmbienteEmision;
import com.bomerp.facturacion.comprobante.entity.Comprobante.EstadoSunat;
import com.bomerp.facturacion.correo.XmlUblDePrueba;
import com.bomerp.notificaciones.notificacion.service.NotificadorCorreo.Adjunto;
import com.bomerp.ventas.venta.entity.Venta;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Entrega del comprobante por correo, sin base de datos: qué se envía y qué no (validez según el ambiente REGISTRADO en
 * el comprobante, destinatario), los adjuntos (nombre de SUNAT, XML de la misma emisión leído con un analizador seguro)
 * y la plantilla HTML (datos escapados, sin inventar contacto).
 */
class ReglasCorreoComprobanteTest {

    private static final String RUC = "20521632268";
    private static final EmpresaProperties EMPRESA = new EmpresaProperties("JD REFRIGERACIÓN S.A.C.", RUC, "Jr. Jorge Chávez 184, Breña", null);

    private static Comprobante comprobante(EstadoSunat estado, AmbienteEmision ambiente, boolean conXml) {
        Comprobante c = new Comprobante();
        c.setSerie("F001");
        c.setNumero("00000125");
        c.setTipo(Comprobante.TipoComprobante.FACTURA);
        c.setFechaEmision(LocalDate.of(2026, 11, 8));
        c.setTotal(new BigDecimal("1180.00"));
        c.setEstadoSunat(estado);
        c.registrarAmbiente(ambiente);
        if (conXml) c.getEnvio().setXmlFirmado(new XmlUblDePrueba().bytes());
        return c;
    }

    // ── Escenarios 1 a 5: solo lo que tiene validez tributaria, según el ambiente registrado ──

    @Test
    void aceptadoUObservadoEnProduccionSeEnvia() {
        assertThat(EntregaComprobanteService.motivoParaNoEnviar(comprobante(EstadoSunat.ACEPTADO, AmbienteEmision.PRODUCCION, true))).isNull();
        assertThat(EntregaComprobanteService.motivoParaNoEnviar(comprobante(EstadoSunat.OBSERVADO, AmbienteEmision.PRODUCCION, true))).isNull();
    }

    @Test
    void rechazadoOPendienteNoSeEnvianAunqueSeanDeProduccion() {
        assertThat(EntregaComprobanteService.motivoParaNoEnviar(comprobante(EstadoSunat.RECHAZADO, AmbienteEmision.PRODUCCION, true)))
                .contains("todavía no tiene validez");
        assertThat(EntregaComprobanteService.motivoParaNoEnviar(comprobante(EstadoSunat.PENDIENTE, null, false)))
                .contains("todavía no tiene validez");
    }

    @Test
    void niLaBetaNiElSimuladoNiLoSinAmbienteRegistradoSeEnvian() {
        assertThat(EntregaComprobanteService.motivoParaNoEnviar(comprobante(EstadoSunat.ACEPTADO, AmbienteEmision.BETA, true)))
                .contains("beta de SUNAT");
        assertThat(EntregaComprobanteService.motivoParaNoEnviar(comprobante(EstadoSunat.ACEPTADO, AmbienteEmision.SIMULADO, true)))
                .contains("modo simulado");
        assertThat(EntregaComprobanteService.motivoParaNoEnviar(comprobante(EstadoSunat.ACEPTADO, AmbienteEmision.PRODUCCION, false)))
                .contains("modo simulado");   // sin XML firmado nunca salió a SUNAT
        assertThat(EntregaComprobanteService.motivoParaNoEnviar(comprobante(EstadoSunat.ACEPTADO, AmbienteEmision.NO_REGISTRADO, true)))
                .contains("no se envía como comprobante tributario");
        assertThat(EntregaComprobanteService.motivoParaNoEnviar(comprobante(EstadoSunat.ACEPTADO, null, true)))
                .contains("no se envía como comprobante tributario");
        Comprobante reemplazado = comprobante(EstadoSunat.ACEPTADO, AmbienteEmision.PRODUCCION, true);
        reemplazado.setReemplazado(true);
        assertThat(EntregaComprobanteService.motivoParaNoEnviar(reemplazado)).contains("reemplazado");
    }

    @Test
    void elAmbienteSeRegistraUnaSolaVezYNoCambia() {
        Comprobante c = comprobante(EstadoSunat.ACEPTADO, AmbienteEmision.BETA, true);
        c.registrarAmbiente(AmbienteEmision.PRODUCCION);   // p. ej. el servidor se pasó a producción después
        assertThat(c.getAmbienteEmision()).isEqualTo(AmbienteEmision.BETA);
        assertThat(EntregaComprobanteService.motivoParaNoEnviar(c)).contains("beta de SUNAT");
        assertThat(AmbienteEmision.PRODUCCION.conValidezTributaria()).isTrue();
        assertThat(AmbienteEmision.BETA.conValidezTributaria()).isFalse();
    }

    // ── Escenario 6: el destinatario ──

    @Test
    void sinCorreoOConUnoInvalidoNoSeEnvia() {
        assertThat(EntregaComprobanteService.problemaConDestinatario(null)).contains("no tiene correo registrado");
        assertThat(EntregaComprobanteService.problemaConDestinatario("  ")).contains("no tiene correo registrado");
        assertThat(EntregaComprobanteService.problemaConDestinatario("cliente@")).contains("no es una dirección válida");
        assertThat(EntregaComprobanteService.problemaConDestinatario("a@b.pe, otro@x.com")).contains("no es una dirección válida");
        assertThat(EntregaComprobanteService.problemaConDestinatario("compras@clinica-santa-lucia.com.pe")).isNull();
    }

    // ── Reintentos: esperas crecientes y un máximo ──

    @Test
    void lasEsperasCrecenYTienenUnMaximo() {
        assertThat(EntregaComprobanteService.esperaTras(1, 5)).isEqualTo(Duration.ofMinutes(2));
        assertThat(EntregaComprobanteService.esperaTras(2, 5)).isEqualTo(Duration.ofMinutes(10));
        assertThat(EntregaComprobanteService.esperaTras(4, 5)).isEqualTo(Duration.ofMinutes(120));
        assertThat(EntregaComprobanteService.esperaTras(5, 5)).isNull();
    }

    // ── Escenarios 9 y 15: el XML es exactamente el de este comprobante (analizador XML seguro) ──

    private static String problema(XmlUblDePrueba x, Comprobante c) {
        return AdjuntosComprobante.problemaConXml(x.bytes(), RUC, c);
    }

    @Test
    void unXmlDeEsteComprobanteSeAceptaSinTocarSusBytes() {
        Comprobante c = comprobante(EstadoSunat.ACEPTADO, AmbienteEmision.PRODUCCION, false);
        byte[] xml = new XmlUblDePrueba().bytes();
        byte[] copia = xml.clone();
        assertThat(AdjuntosComprobante.problemaConXml(xml, RUC, c)).isNull();
        assertThat(xml).isEqualTo(copia);
        XmlUblDePrueba conCeros = new XmlUblDePrueba();
        conCeros.id = "F001-00000125";
        assertThat(problema(conCeros, c)).isNull();
    }

    @Test
    void cadaCampoDelXmlTieneQueCoincidir() {
        Comprobante c = comprobante(EstadoSunat.ACEPTADO, AmbienteEmision.PRODUCCION, false);
        XmlUblDePrueba otroNumero = new XmlUblDePrueba(); otroNumero.id = "F001-126";
        XmlUblDePrueba boleta = new XmlUblDePrueba(); boleta.tipo = "03";
        XmlUblDePrueba otraFecha = new XmlUblDePrueba(); otraFecha.fecha = "2026-11-07";
        XmlUblDePrueba otroTotal = new XmlUblDePrueba(); otroTotal.total = "1180.01";
        XmlUblDePrueba dolares = new XmlUblDePrueba(); dolares.moneda = "USD";
        XmlUblDePrueba sinFirma = new XmlUblDePrueba(); sinFirma.firmado = false;
        assertThat(problema(otroNumero, c)).contains("su número es F001-126");
        assertThat(problema(boleta, c)).contains("su tipo es 03");
        assertThat(problema(otraFecha, c)).contains("fecha de emisión es 2026-11-07");
        assertThat(problema(otroTotal, c)).contains("importe total es 1180.01");
        assertThat(problema(sinFirma, c)).contains("no tiene firma digital");

        Venta enSoles = new Venta();
        enSoles.setMoneda(Venta.Moneda.SOLES);
        c.setVenta(enSoles);
        assertThat(problema(dolares, c)).contains("su moneda es USD");
        assertThat(problema(new XmlUblDePrueba(), c)).isNull();
    }

    @Test
    void loQueUnaBusquedaDeTextoAceptabaAhoraSeRechaza() {
        Comprobante c = comprobante(EstadoSunat.ACEPTADO, AmbienteEmision.PRODUCCION, false);
        // El número correcto aparece, pero en una línea, no como cbc:ID del documento
        XmlUblDePrueba enUnaLinea = new XmlUblDePrueba();
        enUnaLinea.id = "F001-999";
        enUnaLinea.idEnUnaLinea = "F001-125";
        assertThat(problema(enUnaLinea, c)).contains("su número es F001-999");
        // El RUC de la empresa aparece, pero como CLIENTE: el emisor es otro
        XmlUblDePrueba otroEmisor = new XmlUblDePrueba();
        otroEmisor.rucEmisor = "20999999999";
        otroEmisor.rucCliente = RUC;
        assertThat(problema(otroEmisor, c)).contains("el RUC del emisor es 20999999999");
    }

    @Test
    void elAnalizadorRechazaDoctypeEntidadesYXmlRotos() {
        Comprobante c = comprobante(EstadoSunat.ACEPTADO, AmbienteEmision.PRODUCCION, false);
        XmlUblDePrueba xxe = new XmlUblDePrueba();
        xxe.prologo = "<!DOCTYPE Invoice [<!ENTITY x SYSTEM \"file:///C:/Windows/win.ini\">]>\n";
        assertThat(problema(xxe, c)).contains("no se pudo leer").contains("DOCTYPE");
        assertThat(AdjuntosComprobante.problemaConXml("<Invoice><sin cerrar>".getBytes(), RUC, c)).contains("no se pudo leer");
        assertThat(AdjuntosComprobante.problemaConXml("<Otra xmlns=\"x\"/>".getBytes(), RUC, c)).contains("no es una factura o boleta UBL");
        assertThat(AdjuntosComprobante.problemaConXml(null, RUC, c)).contains("no tiene XML firmado");
    }

    @Test
    void losAdjuntosSonDelMismoComprobanteYElXmlNoSeToca() {
        Comprobante c = comprobante(EstadoSunat.ACEPTADO, AmbienteEmision.PRODUCCION, false);
        byte[] xml = new XmlUblDePrueba().bytes();
        List<Adjunto> adj = AdjuntosComprobante.armar(RUC, c, new byte[]{1, 2, 3}, xml);
        assertThat(adj).extracting(Adjunto::nombre).containsExactly("20521632268-01-F001-125.pdf", "20521632268-01-F001-125.xml");
        assertThat(adj.get(1).contenido()).isSameAs(xml);
        assertThat(adj).extracting(Adjunto::tipoMime).containsExactly("application/pdf", "application/xml");
        assertThat(AdjuntosComprobante.resumen(adj)).contains("20521632268-01-F001-125.xml").contains("sha256 ");
        c.setTipo(Comprobante.TipoComprobante.BOLETA);
        c.setSerie("B001");
        assertThat(AdjuntosComprobante.nombreBase(RUC, c)).isEqualTo("20521632268-03-B001-125");
    }

    // ── Plantilla HTML ──

    private static PlantillaCorreoComprobante.Datos datos(String cliente, boolean observado) {
        return new PlantillaCorreoComprobante.Datos("FACTURA", "F001-00000125", LocalDate.of(2026, 11, 8), "SOLES",
                new BigDecimal("1180.00"), observado, cliente, List.of("20521632268-01-F001-125.pdf", "20521632268-01-F001-125.xml"));
    }

    @Test
    void laPlantillaTieneLosDatosDelComprobante() {
        String html = PlantillaCorreoComprobante.html(datos("Clínica Santa Lucía S.A.C.", false), EMPRESA);
        assertThat(html).contains("Has recibido tu comprobante electrónico")
                .contains("Clínica Santa Lucía S.A.C.")
                .contains("Factura electrónica").contains("F001-00000125").contains("08/11/2026")
                .contains("S/ 1,180.00").contains("Aceptado por SUNAT")
                .contains("20521632268-01-F001-125.pdf").contains("20521632268-01-F001-125.xml")
                .contains("RUC 20521632268").contains("Jr. Jorge Chávez 184, Breña")
                .contains("cid:logo");
        assertThat(PlantillaCorreoComprobante.html(datos("X", true), EMPRESA)).contains("Aceptado por SUNAT con observaciones");
        assertThat(PlantillaCorreoComprobante.asunto(datos("X", false), EMPRESA))
                .isEqualTo("Factura electrónica F001-00000125 — JD REFRIGERACIÓN S.A.C.");
    }

    @Test
    void laPlantillaEscapaLosDatosYNoUsaRecursosRemotosNiScripts() {
        String html = PlantillaCorreoComprobante.html(datos("<script>alert(1)</script><img src=x onerror=y>", false), EMPRESA);
        assertThat(html).doesNotContain("<script>").doesNotContain("<img src=x")
                .contains("&lt;script&gt;alert(1)&lt;/script&gt;");
        assertThat(html).doesNotContain("src=\"http").doesNotContain("<link").doesNotContain("<style");
    }

    @Test
    void elContactoSoloApareceSiEstaConfigurado() {
        String sin = PlantillaCorreoComprobante.html(datos("X", false), EMPRESA);
        assertThat(sin).doesNotContain("Teléfono:").doesNotContain("mailto:").doesNotContain("Web:");
        EmpresaProperties conContacto = new EmpresaProperties("JD REFRIGERACIÓN S.A.C.", RUC, "Breña", null,
                "(01) 555-0000", "ventas@ejemplo.test", "ejemplo.test");
        String con = PlantillaCorreoComprobante.html(datos("X", false), conContacto);
        assertThat(con).contains("Teléfono: (01) 555-0000").contains("mailto:ventas@ejemplo.test").contains("https://ejemplo.test");
    }
}
