package com.bomerp.facturacion.correo.service;

import com.bomerp.config.empresa.EmpresaProperties;
import com.bomerp.facturacion.comprobante.entity.Comprobante;
import com.bomerp.facturacion.comprobante.entity.Comprobante.EstadoSunat;
import com.bomerp.notificaciones.notificacion.service.NotificadorCorreo.Adjunto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Entrega del comprobante por correo, sin base de datos: qué se envía y qué no (validez tributaria, destinatario),
 * los adjuntos (nombre de SUNAT, XML de la misma emisión) y la plantilla HTML (datos escapados, sin inventar contacto).
 */
class ReglasCorreoComprobanteTest {

    private static final String RUC = "20521632268";
    private static final EmpresaProperties EMPRESA = new EmpresaProperties("JD REFRIGERACIÓN S.A.C.", RUC, "Jr. Jorge Chávez 184, Breña", null);

    private static Comprobante comprobante(EstadoSunat estado, byte[] xml) {
        Comprobante c = new Comprobante();
        c.setSerie("F001");
        c.setNumero("00000125");
        c.setTipo(Comprobante.TipoComprobante.FACTURA);
        c.setEstadoSunat(estado);
        c.getEnvio().setXmlFirmado(xml);
        return c;
    }

    private static byte[] xmlDe(String id, String ruc) {
        return ("<Invoice><cbc:ID>" + id + "</cbc:ID><cbc:ID schemeID=\"6\">" + ruc + "</cbc:ID></Invoice>").getBytes(StandardCharsets.UTF_8);
    }

    // ── Escenarios 1 a 5: solo lo que tiene validez tributaria ──

    @Test
    void aceptadoConValidezSeEnvia() {
        assertThat(EntregaComprobanteService.motivoParaNoEnviar(comprobante(EstadoSunat.ACEPTADO, xmlDe("F001-125", RUC)), true)).isNull();
    }

    @Test
    void observadoConValidezTambienSeEnvia() {
        assertThat(EntregaComprobanteService.motivoParaNoEnviar(comprobante(EstadoSunat.OBSERVADO, xmlDe("F001-125", RUC)), true)).isNull();
    }

    @Test
    void rechazadoNoSeEnvia() {
        assertThat(EntregaComprobanteService.motivoParaNoEnviar(comprobante(EstadoSunat.RECHAZADO, xmlDe("F001-125", RUC)), true))
                .contains("todavía no tiene validez");
    }

    @Test
    void pendienteNoSeEnvia() {
        assertThat(EntregaComprobanteService.motivoParaNoEnviar(comprobante(EstadoSunat.PENDIENTE, null), true))
                .contains("todavía no tiene validez");
    }

    @Test
    void niLaBetaNiElSimuladoNiUnReemplazadoSeEnvian() {
        assertThat(EntregaComprobanteService.motivoParaNoEnviar(comprobante(EstadoSunat.ACEPTADO, xmlDe("F001-125", RUC)), false))
                .contains("beta de SUNAT");
        assertThat(EntregaComprobanteService.motivoParaNoEnviar(comprobante(EstadoSunat.ACEPTADO, null), true))
                .contains("modo simulado");
        Comprobante reemplazado = comprobante(EstadoSunat.ACEPTADO, xmlDe("F001-125", RUC));
        reemplazado.setReemplazado(true);
        assertThat(EntregaComprobanteService.motivoParaNoEnviar(reemplazado, true)).contains("reemplazado");
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
        assertThat(EntregaComprobanteService.esperaTras(5, 5)).isNull();   // agotó los intentos automáticos
    }

    // ── Escenarios 9 y 15: el XML es exactamente el de este comprobante ──

    @Test
    void elXmlTieneQueSerDeEsteComprobante() {
        Comprobante c = comprobante(EstadoSunat.ACEPTADO, null);
        assertThat(AdjuntosComprobante.problemaConXml(xmlDe("F001-125", RUC), RUC, c)).isNull();
        assertThat(AdjuntosComprobante.problemaConXml(xmlDe("F001-00000125", RUC), RUC, c)).isNull();
        assertThat(AdjuntosComprobante.problemaConXml(null, RUC, c)).contains("no tiene XML firmado");
        assertThat(AdjuntosComprobante.problemaConXml(xmlDe("F001-126", RUC), RUC, c)).contains("no corresponde a F001-00000125");
        assertThat(AdjuntosComprobante.problemaConXml(xmlDe("F001-1250", RUC), RUC, c)).contains("no corresponde");
        assertThat(AdjuntosComprobante.problemaConXml(xmlDe("F001-125", "20999999999"), RUC, c)).contains("no tiene el RUC");
    }

    @Test
    void losAdjuntosSonDelMismoComprobanteYElXmlNoSeToca() {
        Comprobante c = comprobante(EstadoSunat.ACEPTADO, null);
        byte[] xml = xmlDe("F001-125", RUC);
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
    void elContactoSoloAparecesiEstaConfigurado() {
        String sin = PlantillaCorreoComprobante.html(datos("X", false), EMPRESA);
        assertThat(sin).doesNotContain("Teléfono:").doesNotContain("mailto:").doesNotContain("Web:");
        EmpresaProperties conContacto = new EmpresaProperties("JD REFRIGERACIÓN S.A.C.", RUC, "Breña", null,
                "(01) 555-0000", "ventas@ejemplo.test", "ejemplo.test");
        String con = PlantillaCorreoComprobante.html(datos("X", false), conContacto);
        assertThat(con).contains("Teléfono: (01) 555-0000").contains("mailto:ventas@ejemplo.test").contains("https://ejemplo.test");
    }
}
