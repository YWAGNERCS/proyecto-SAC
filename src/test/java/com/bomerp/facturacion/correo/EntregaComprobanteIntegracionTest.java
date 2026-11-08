package com.bomerp.facturacion.correo;

import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.catalogo.producto.repository.ProductoRepository;
import com.bomerp.facturacion.comprobante.entity.Comprobante;
import com.bomerp.facturacion.correo.entity.EnvioCorreo;
import com.bomerp.facturacion.correo.repository.EnvioCorreoRepository;
import com.bomerp.facturacion.correo.service.AdjuntosComprobante;
import com.bomerp.facturacion.correo.service.EntregaComprobanteService;
import com.bomerp.facturacion.facturacion.service.EnvioSunatService;
import com.bomerp.facturacion.ose.integration.OseClient;
import com.bomerp.facturacion.ose.integration.OseClient.RespuestaOse;
import com.bomerp.facturacion.pdf.service.GeneradorPdfFacturacionService;
import com.bomerp.notificaciones.notificacion.service.NotificadorCorreo;
import com.bomerp.notificaciones.notificacion.service.NotificadorCorreo.CorreoNoEnviadoException;
import com.bomerp.notificaciones.notificacion.service.NotificadorCorreo.MensajeCorreo;
import com.bomerp.ventas.venta.entity.Venta;
import com.bomerp.ventas.venta.service.VentaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Entrega del comprobante por correo contra Oracle, de punta a punta: venta → comprobante → SUNAT (simulada aquí) acepta
 * → solicitud de envío → servidor de correo (simulado: no sale ningún correo) → resultado guardado. También los fallos:
 * SMTP caído, PDF que no se genera, XML que no corresponde, eventos repetidos, dos trabajadores a la vez, reinicio del
 * backend y la caída entre la aceptación del servidor SMTP y su registro en Oracle.
 *
 * No es @Transactional: el envío corre en otros hilos y necesita ver los datos confirmados. Lo que crea se borra al
 * terminar (ventas, comprobantes, envíos, el producto) y las series vuelven a su número.
 */
@Tag("oracle")
@SpringBootTest(properties = {
        "app.facturacion.reintento-inicial-ms=86400000",
        "app.facturacion.pausa-entre-envios-ms=0",
        "app.correo.revision-inicial-ms=86400000",
        "app.correo.minutos-en-proceso=10"})
class EntregaComprobanteIntegracionTest {

    @MockitoBean private OseClient ose;
    @MockitoBean private NotificadorCorreo notificador;
    @MockitoSpyBean private GeneradorPdfFacturacionService generadorPdf;

    @Autowired private VentaService ventaService;
    @Autowired private EntregaComprobanteService entrega;
    @Autowired private EnvioCorreoRepository envios;
    @Autowired private ProductoRepository productos;
    @Autowired private ApplicationEventPublisher eventos;
    @Autowired private JdbcTemplate jdbc;
    @Value("${app.empresa.ruc}") private String ruc;

    private Long conCorreo;
    private Long sinCorreo;
    private Long productoId;
    private final List<Long> ventas = new ArrayList<>();
    private Map<String, Long> seriesAntes;

    @BeforeEach
    void preparar() {
        conCorreo = jdbc.queryForObject("SELECT MIN(id) FROM clientes WHERE agente_retencion = 0 AND correo IS NOT NULL", Long.class);
        sinCorreo = jdbc.queryForObject("SELECT MIN(id) FROM clientes WHERE agente_retencion = 0 AND correo IS NULL", Long.class);
        seriesAntes = new HashMap<>();
        jdbc.query("SELECT serie, ultimo_numero FROM series_comprobante", rs -> {
            seriesAntes.put(rs.getString(1), rs.getLong(2));
        });
        Producto p = new Producto();
        p.setNombre("Visita de prueba (correo del comprobante)");
        p.setSector(Producto.Sector.SERVICIO);
        p.setCostoBase(new BigDecimal("100.00"));
        p.setMargenPorcentaje(BigDecimal.ZERO);
        p.setMoneda(Producto.Moneda.SOLES);
        productoId = productos.save(p).getId();

        // SUNAT (simulada) acepta y devuelve el XML firmado de ESE comprobante y su CDR
        when(ose.emitir(any())).thenAnswer(i -> {
            Comprobante c = i.getArgument(0);
            byte[] xml = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Invoice><cbc:ID>" + c.getSerie() + "-"
                    + Integer.parseInt(c.getNumero()) + "</cbc:ID><cbc:ID schemeID=\"6\">" + ruc
                    + "</cbc:ID><ds:SignatureValue>ñandú==</ds:SignatureValue></Invoice>").getBytes(StandardCharsets.UTF_8);
            return new RespuestaOse(OseClient.Resultado.ACEPTADO, 0, "Aceptado (prueba)", null, null, xml, new byte[]{80, 75});
        });
        when(ose.conValidezTributaria()).thenReturn(true);
        when(notificador.enviarConAdjuntos(any())).thenAnswer(i -> ((MensajeCorreo) i.getArgument(0)).messageId());
    }

    @AfterEach
    void limpiar() {
        for (Long v : ventas) {
            jdbc.update("DELETE FROM intentos_correo WHERE envio_correo_id IN (SELECT e.id FROM envios_correo e "
                    + "JOIN comprobantes c ON c.id = e.comprobante_id WHERE c.venta_id = ?)", v);
            jdbc.update("DELETE FROM envios_correo WHERE comprobante_id IN (SELECT id FROM comprobantes WHERE venta_id = ?)", v);
            jdbc.update("DELETE FROM envios_sunat WHERE comprobante_id IN (SELECT id FROM comprobantes WHERE venta_id = ?)", v);
            jdbc.update("DELETE FROM comprobantes WHERE venta_id = ?", v);
            jdbc.update("DELETE FROM detalle_venta WHERE venta_id = ?", v);
            jdbc.update("DELETE FROM ventas WHERE id = ?", v);
        }
        jdbc.update("DELETE FROM productos WHERE id = ?", productoId);
        seriesAntes.forEach((serie, n) -> jdbc.update("UPDATE series_comprobante SET ultimo_numero = ? WHERE serie = ?", n, serie));
    }

    // ── Ayudas ──

    private static void esperar(BooleanSupplier condicion, String que) throws InterruptedException {
        long fin = System.currentTimeMillis() + 15000;
        while (!condicion.getAsBoolean()) {
            if (System.currentTimeMillis() > fin) throw new AssertionError("No pasó a tiempo: " + que);
            Thread.sleep(100);
        }
    }

    /** Venta real (confirmada); su comprobante sale a SUNAT (simulada) en segundo plano y queda ACEPTADO. */
    private Long venderYAceptar(Long clienteId) throws InterruptedException {
        Long ventaId = ventaService.registrarVentaDirecta(clienteId,
                List.of(new VentaService.ItemVentaDTO(productoId, 1, BigDecimal.ZERO)),
                Venta.TipoPago.CONTADO, null, Venta.Moneda.SOLES, null, "prueba", "prueba", null, null).id();
        ventas.add(ventaId);
        esperar(() -> "ACEPTADO".equals(estadoSunat(ventaId)), "SUNAT acepta la venta " + ventaId);
        return comprobanteDe(ventaId);
    }

    private String estadoSunat(Long ventaId) {
        List<String> r = jdbc.queryForList("SELECT estado_sunat FROM comprobantes WHERE venta_id = ?", String.class, ventaId);
        return r.isEmpty() ? null : r.get(0);
    }

    private Long comprobanteDe(Long ventaId) {
        return jdbc.queryForObject("SELECT id FROM comprobantes WHERE venta_id = ?", Long.class, ventaId);
    }

    private List<EnvioCorreo> enviosDe(Long comprobanteId) {
        return envios.findByComprobanteIdOrderByIdDesc(comprobanteId);
    }

    private String estadoCorreo(Long comprobanteId) {
        List<EnvioCorreo> l = enviosDe(comprobanteId);
        return l.isEmpty() ? null : l.get(0).getEstado().name();
    }

    /** Un comprobante aceptado SIN su envío automático (para preparar cada caso a mano). */
    private Long comprobanteSinEnvioAutomatico() throws InterruptedException {
        when(ose.conValidezTributaria()).thenReturn(false);   // como la beta: no se pide el envío automático
        Long id = venderYAceptar(conCorreo);
        Thread.sleep(300);
        assertThat(enviosDe(id)).isEmpty();
        when(ose.conValidezTributaria()).thenReturn(true);
        return id;
    }

    private Long envioPendiente(Long comprobanteId) {
        String correo = jdbc.queryForObject("SELECT correo FROM clientes WHERE id = ?", String.class, conCorreo);
        return envios.save(EnvioCorreo.nuevo(comprobanteId, correo, EnvioCorreo.Tipo.MANUAL, "prueba")).getId();
    }

    // ── 1 y 15: SUNAT acepta → el servidor SMTP acepta → ENVIADO, con el PDF y el XML de ESE comprobante ──

    @Test
    void sunatAceptaYElServidorDeCorreoAceptaQuedaEnviadoConPdfYXmlDelMismoComprobante() throws Exception {
        Long id = venderYAceptar(conCorreo);
        esperar(() -> "ENVIADO".equals(estadoCorreo(id)), "correo ENVIADO");

        EnvioCorreo e = enviosDe(id).get(0);
        assertThat(e.getTipo()).isEqualTo(EnvioCorreo.Tipo.AUTOMATICO);
        assertThat(e.getIntentos()).isEqualTo(1);
        assertThat(e.getFechaAceptacionSmtp()).isNotNull();
        assertThat(e.getMessageId()).startsWith("<").endsWith(">");
        assertThat(e.getDestinatario()).isEqualTo(jdbc.queryForObject("SELECT correo FROM clientes WHERE id = ?", String.class, conCorreo));

        ArgumentCaptor<MensajeCorreo> m = ArgumentCaptor.forClass(MensajeCorreo.class);
        verify(notificador, times(1)).enviarConAdjuntos(m.capture());
        Map<String, Object> fila = jdbc.queryForMap("SELECT serie, numero, tipo, sunat_xml FROM comprobantes WHERE id = ?", id);
        String base = ruc + "-" + ("FACTURA".equals(fila.get("TIPO")) ? "01" : "03") + "-" + fila.get("SERIE") + "-"
                + Integer.parseInt((String) fila.get("NUMERO"));
        assertThat(m.getValue().adjuntos()).extracting(NotificadorCorreo.Adjunto::nombre).containsExactly(base + ".pdf", base + ".xml");
        // El XML adjunto es, byte por byte, el que está guardado en Oracle (el que se envió a SUNAT)
        assertThat(m.getValue().adjuntos().get(1).contenido()).isEqualTo((byte[]) fila.get("SUNAT_XML"));
        assertThat(new String(m.getValue().adjuntos().get(0).contenido(), 0, 4, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");
        assertThat(m.getValue().asunto()).contains(fila.get("SERIE") + "-" + fila.get("NUMERO"));
        assertThat(m.getValue().html()).contains("Has recibido tu comprobante electrónico");
        assertThat(jdbc.queryForObject("SELECT resultado || ' | ' || adjuntos FROM intentos_correo WHERE envio_correo_id = ?",
                String.class, e.getId())).startsWith("ENVIADO").contains(base + ".pdf").contains(base + ".xml").contains("sha256");
    }

    // ── 5 y 6: beta o cliente sin correo → no se envía (y el reenvío manual lo explica) ──

    @Test
    void conLaBetaOSinCorreoNoSeEnviaYElReenvioManualDiceElMotivo() throws Exception {
        Long beta = comprobanteSinEnvioAutomatico();
        when(ose.conValidezTributaria()).thenReturn(false);
        assertThatThrownBy(() -> entrega.solicitarReenvio(beta, true, "lisandro"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("beta de SUNAT");
        when(ose.conValidezTributaria()).thenReturn(true);

        Long sinMail = venderYAceptar(sinCorreo);
        Thread.sleep(300);
        assertThat(enviosDe(sinMail)).isEmpty();
        assertThat(entrega.entrega(sinMail).motivoNoSeEnvia()).contains("no tiene correo registrado");
        assertThatThrownBy(() -> entrega.solicitarReenvio(sinMail, true, "lisandro"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("no tiene correo registrado");
        verify(notificador, never()).enviarConAdjuntos(any());
    }

    // ── 7 y 10: el servidor SMTP falla → FALLIDO con reintento; la revisión (también al reiniciar) lo envía ──

    @Test
    void siElServidorDeCorreoFallaQuedaFallidoYLaRevisionLoReintentaSinTocarLaVenta() throws Exception {
        doThrow(new CorreoNoEnviadoException("El servidor de correo no aceptó el mensaje: 421 ocupado")).when(notificador).enviarConAdjuntos(any());
        Long id = venderYAceptar(conCorreo);
        esperar(() -> "FALLIDO".equals(estadoCorreo(id)), "correo FALLIDO");

        EnvioCorreo e = enviosDe(id).get(0);
        assertThat(e.getUltimoError()).contains("421 ocupado").contains("Se vuelve a intentar solo");
        assertThat(e.getProximoIntento()).isAfter(LocalDateTime.now().plusMinutes(1));
        Map<String, Object> antes = jdbc.queryForMap("SELECT serie, numero, estado_sunat FROM comprobantes WHERE id = ?", id);

        // Reinicio del backend: la revisión periódica retoma lo que ya le toca
        jdbc.update("UPDATE envios_correo SET proximo_intento = ? WHERE id = ?", LocalDateTime.now().minusSeconds(1), e.getId());
        reset(notificador);
        when(notificador.enviarConAdjuntos(any())).thenAnswer(i -> ((MensajeCorreo) i.getArgument(0)).messageId());
        entrega.revisar();

        EnvioCorreo despues = enviosDe(id).get(0);
        assertThat(despues.getEstado()).isEqualTo(EnvioCorreo.Estado.ENVIADO);
        assertThat(despues.getIntentos()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM intentos_correo WHERE envio_correo_id = ?", Integer.class, e.getId())).isEqualTo(2);
        // Ni otro comprobante, ni otro número, ni otra venta
        assertThat(jdbc.queryForMap("SELECT serie, numero, estado_sunat FROM comprobantes WHERE id = ?", id)).isEqualTo(antes);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comprobantes WHERE venta_id = ?", Integer.class, ventas.get(0))).isEqualTo(1);
    }

    @Test
    void alReiniciarSeEnvianLosPendientesQueQuedaron() throws Exception {
        Long id = comprobanteSinEnvioAutomatico();
        Long envio = envioPendiente(id);   // quedó PENDIENTE cuando se apagó el backend (nunca entró a la cola)
        entrega.revisar();
        assertThat(envios.findById(envio).orElseThrow().getEstado()).isEqualTo(EnvioCorreo.Estado.ENVIADO);
    }

    // ── 8: el PDF no se genera → no se manda un correo incompleto ──

    @Test
    void siElPdfNoSeGeneraNoSeEnviaUnCorreoIncompleto() throws Exception {
        doThrow(new IllegalStateException("fuente dañada")).when(generadorPdf).generarFacturaPdfBytes(any());
        Long id = venderYAceptar(conCorreo);
        esperar(() -> "FALLIDO".equals(estadoCorreo(id)), "correo FALLIDO por el PDF");
        assertThat(enviosDe(id).get(0).getUltimoError()).contains("No se pudo generar el PDF").contains("fuente dañada");
        assertThat(enviosDe(id).get(0).getProximoIntento()).isNotNull();   // puede ser pasajero: se reintenta
        verify(notificador, never()).enviarConAdjuntos(any());
    }

    // ── 9 y 15: sin XML, o un XML que no es de este comprobante → no se envía ──

    @Test
    void sinXmlOConElXmlDeOtroComprobanteNoSeEnvia() throws Exception {
        Long id = comprobanteSinEnvioAutomatico();

        jdbc.update("UPDATE comprobantes SET sunat_xml = ? WHERE id = ?",
                ("<Invoice><cbc:ID>X999-1</cbc:ID>" + ruc + "</Invoice>").getBytes(StandardCharsets.UTF_8), id);
        Long envio = envioPendiente(id);
        assertThat(entrega.procesar(envio)).isEqualTo(EnvioCorreo.Estado.FALLIDO);
        EnvioCorreo e = envios.findById(envio).orElseThrow();
        assertThat(e.getUltimoError()).contains("no corresponde");
        assertThat(e.getProximoIntento()).isNull();   // definitivo: no se reintenta solo

        jdbc.update("UPDATE comprobantes SET sunat_xml = NULL WHERE id = ?", id);
        assertThatThrownBy(() -> entrega.solicitarReenvio(id, true, "lisandro"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("modo simulado");
        verify(notificador, never()).enviarConAdjuntos(any());
    }

    // ── 11: SUNAT avisa dos veces la aceptación → un solo envío automático ──

    @Test
    void siElEventoDeAceptacionLlegaDosVecesSeEnviaUnaSolaVez() throws Exception {
        Long id = comprobanteSinEnvioAutomatico();
        ExecutorService dos = Executors.newFixedThreadPool(2);
        CountDownLatch largada = new CountDownLatch(1);
        for (int i = 0; i < 2; i++) {
            dos.submit(() -> {
                largada.await();
                eventos.publishEvent(new EnvioSunatService.ComprobanteAceptadoEvent(id));
                return null;
            });
        }
        largada.countDown();
        dos.shutdown();
        dos.awaitTermination(10, TimeUnit.SECONDS);
        eventos.publishEvent(new EnvioSunatService.ComprobanteAceptadoEvent(id));   // y una tercera, más tarde
        esperar(() -> "ENVIADO".equals(estadoCorreo(id)), "correo ENVIADO");
        Thread.sleep(500);

        assertThat(enviosDe(id)).hasSize(1);
        verify(notificador, times(1)).enviarConAdjuntos(any());
    }

    // ── 12: dos trabajadores intentan enviar el mismo comprobante a la vez → uno solo lo envía ──

    @Test
    void dosTrabajadoresALaVezEnvianUnaSolaVez() throws Exception {
        Long id = comprobanteSinEnvioAutomatico();
        Long envio = envioPendiente(id);
        CountDownLatch dentro = new CountDownLatch(1);
        doAnswer(i -> {
            dentro.countDown();
            Thread.sleep(800);   // el servidor SMTP tarda: el otro trabajador llega mientras tanto
            return ((MensajeCorreo) i.getArgument(0)).messageId();
        }).when(notificador).enviarConAdjuntos(any());

        ExecutorService dos = Executors.newFixedThreadPool(2);
        CountDownLatch largada = new CountDownLatch(1);
        Future<EnvioCorreo.Estado> a = dos.submit(() -> { largada.await(); return entrega.procesar(envio); });
        Future<EnvioCorreo.Estado> b = dos.submit(() -> { largada.await(); return entrega.procesar(envio); });
        largada.countDown();

        // Mientras uno envía, un reenvío manual del mismo comprobante tampoco entra
        dentro.await(10, TimeUnit.SECONDS);
        assertThatThrownBy(() -> entrega.solicitarReenvio(id, true, "lisandro"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("en curso");

        List<EnvioCorreo.Estado> resultados = Arrays.asList(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
        dos.shutdown();
        assertThat(resultados).containsExactlyInAnyOrder(EnvioCorreo.Estado.ENVIADO, null);
        verify(notificador, times(1)).enviarConAdjuntos(any());
        assertThat(envios.findById(envio).orElseThrow().getIntentos()).isEqualTo(1);
    }

    // ── 13: reenvío manual de un comprobante ya enviado → hay que confirmarlo ──

    @Test
    void elReenvioDeUnoYaEnviadoPideConfirmacion() throws Exception {
        Long id = venderYAceptar(conCorreo);
        esperar(() -> "ENVIADO".equals(estadoCorreo(id)), "correo ENVIADO");

        assertThatThrownBy(() -> entrega.solicitarReenvio(id, false, "lucy"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("ya se envió");
        entrega.solicitarReenvio(id, true, "lisandro");
        esperar(() -> enviosDe(id).size() == 2 && "ENVIADO".equals(estadoCorreo(id)), "reenvío ENVIADO");

        EnvioCorreo manual = enviosDe(id).get(0);
        assertThat(manual.getTipo()).isEqualTo(EnvioCorreo.Tipo.MANUAL);
        assertThat(manual.getSolicitadoPor()).isEqualTo("lisandro");
        verify(notificador, times(2)).enviarConAdjuntos(any());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comprobantes WHERE venta_id = ?", Integer.class, ventas.get(0))).isEqualTo(1);
    }

    // ── 14: se cae entre la aceptación del servidor SMTP y su registro en Oracle → INCIERTO, sin reenvío solo ──

    @Test
    void siSeCaeEntreLaAceptacionDelSmtpYOracleQuedaInciertoYNoSeReenviaSolo() throws Exception {
        Long id = comprobanteSinEnvioAutomatico();
        Long envio = envioPendiente(id);
        // El estado que deja la caída: tomado (EN_PROCESO, con su Message-ID ya guardado) y nunca registrado
        jdbc.update("UPDATE envios_correo SET estado = 'EN_PROCESO', intentos = 1, message_id = '<caido@prueba.test>', "
                + "en_proceso_desde = ?, fecha_ultimo_intento = ? WHERE id = ?",
                LocalDateTime.now().minusMinutes(20), LocalDateTime.now().minusMinutes(20), envio);

        entrega.revisar();

        EnvioCorreo e = envios.findById(envio).orElseThrow();
        assertThat(e.getEstado()).isEqualTo(EnvioCorreo.Estado.INCIERTO);
        assertThat(e.getUltimoError()).contains("no se sabe si el servidor de correo llegó a aceptarlo").contains("<caido@prueba.test>");
        assertThat(e.getProximoIntento()).isNull();
        assertThat(jdbc.queryForObject("SELECT resultado FROM intentos_correo WHERE envio_correo_id = ?", String.class, envio)).isEqualTo("INCIERTO");
        entrega.revisar();   // otra vuelta: tampoco lo toca
        verify(notificador, never()).enviarConAdjuntos(any());
        assertThat(envios.findById(envio).orElseThrow().getEstado()).isEqualTo(EnvioCorreo.Estado.INCIERTO);

        // Lisandro decide reenviarlo: tiene que confirmarlo
        assertThatThrownBy(() -> entrega.solicitarReenvio(id, false, "lisandro")).hasMessageContaining("ya se envió");
        entrega.solicitarReenvio(id, true, "lisandro");
        esperar(() -> "ENVIADO".equals(estadoCorreo(id)), "reenvío confirmado ENVIADO");
    }

    @Test
    void elNombreDeLosAdjuntosEsElDeSunat() throws Exception {
        Long id = comprobanteSinEnvioAutomatico();
        Map<String, Object> f = jdbc.queryForMap("SELECT serie, numero, tipo FROM comprobantes WHERE id = ?", id);
        Comprobante c = new Comprobante();
        c.setSerie((String) f.get("SERIE"));
        c.setNumero((String) f.get("NUMERO"));
        c.setTipo(Comprobante.TipoComprobante.valueOf((String) f.get("TIPO")));
        assertThat(AdjuntosComprobante.nombreBase(ruc, c)).matches(ruc + "-0[13]-[FB]001-\\d+");
    }
}
