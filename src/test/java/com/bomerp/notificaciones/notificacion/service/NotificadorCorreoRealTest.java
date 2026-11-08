package com.bomerp.notificaciones.notificacion.service;

import com.bomerp.notificaciones.notificacion.service.NotificadorCorreo.Adjunto;
import com.bomerp.notificaciones.notificacion.service.NotificadorCorreo.CorreoNoEnviadoException;
import com.bomerp.notificaciones.notificacion.service.NotificadorCorreo.MensajeCorreo;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El correo con adjuntos contra un servidor SMTP de prueba en la misma PC (ningún correo sale de verdad): los
 * adjuntos llegan con sus bytes exactos, se respeta el Message-ID propio, y si el servidor rechaza o no responde el
 * error se informa (no se esconde como antes con un "false" en el log).
 */
class NotificadorCorreoRealTest {

    private ServidorSmtpDePrueba smtp;
    private NotificadorCorreoReal notificador;

    /** XML "firmado" de prueba con lo que un envío como texto cambiaría (tildes, espacios, saltos de línea LF). */
    private static final byte[] XML = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<Invoice>\n  <cbc:ID>B001-7</cbc:ID>"
            + "<ds:SignatureValue>áéí ñ  ==</ds:SignatureValue></Invoice>").getBytes(StandardCharsets.UTF_8);
    private static final byte[] PDF = "%PDF-1.4 prueba\n%%EOF".getBytes(StandardCharsets.ISO_8859_1);

    @BeforeEach
    void preparar() throws Exception {
        smtp = new ServidorSmtpDePrueba();
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("127.0.0.1");
        sender.setPort(smtp.puerto());
        Properties p = sender.getJavaMailProperties();
        p.put("mail.smtp.connectiontimeout", "2000");
        p.put("mail.smtp.timeout", "2000");
        p.put("mail.smtp.writetimeout", "2000");
        notificador = new NotificadorCorreoReal(sender, "facturacion@prueba.test");
    }

    @AfterEach
    void cerrar() throws Exception {
        smtp.close();
    }

    private static MensajeCorreo mensaje() {
        return new MensajeCorreo("cliente@prueba.test", "Boleta de venta electrónica B001-00000007", "<p>Hola</p>",
                List.of(new Adjunto("20521632268-03-B001-7.pdf", PDF, "application/pdf"),
                        new Adjunto("20521632268-03-B001-7.xml", XML, "application/xml")),
                "<abc-123.sgejd-1@prueba.test>");
    }

    /** Adjuntos del mensaje recibido: nombre → bytes. */
    private static Map<String, byte[]> adjuntos(String crudo) throws Exception {
        MimeMessage m = new MimeMessage(Session.getInstance(new Properties()),
                new ByteArrayInputStream(crudo.getBytes(StandardCharsets.ISO_8859_1)));
        Map<String, byte[]> r = new LinkedHashMap<>();
        recorrer(m, r);
        return r;
    }

    private static void recorrer(Part parte, Map<String, byte[]> r) throws Exception {
        if (parte.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) parte.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                BodyPart bp = mp.getBodyPart(i);
                recorrer(bp, r);
            }
        } else if (Part.ATTACHMENT.equalsIgnoreCase(parte.getDisposition())) {
            r.put(parte.getFileName(), parte.getInputStream().readAllBytes());
        }
    }

    @Test
    void elServidorAceptaYLlegaElPdfYElXmlConSusBytesExactos() throws Exception {
        String id = notificador.enviarConAdjuntos(mensaje());

        assertThat(id).isEqualTo("<abc-123.sgejd-1@prueba.test>");
        assertThat(smtp.mensajes()).hasSize(1);
        String crudo = smtp.mensajes().get(0);
        assertThat(crudo).contains("Message-ID: <abc-123.sgejd-1@prueba.test>");
        assertThat(crudo).contains("Content-Transfer-Encoding: base64");
        Map<String, byte[]> adj = adjuntos(crudo);
        assertThat(adj.keySet()).containsExactly("20521632268-03-B001-7.pdf", "20521632268-03-B001-7.xml");
        assertThat(adj.get("20521632268-03-B001-7.xml")).isEqualTo(XML);   // el XML firmado, sin un solo cambio
        assertThat(adj.get("20521632268-03-B001-7.pdf")).isEqualTo(PDF);
    }

    @Test
    void siElServidorRechazaSeInformaElMotivo() {
        smtp.modo(ServidorSmtpDePrueba.Modo.RECHAZAR);
        assertThatThrownBy(() -> notificador.enviarConAdjuntos(mensaje()))
                .isInstanceOf(CorreoNoEnviadoException.class).hasMessageContaining("no aceptó");
        assertThat(smtp.mensajes()).isEmpty();
    }

    @Test
    void siElServidorNoRespondeSeRindePorTiempoYLoInforma() {
        smtp.modo(ServidorSmtpDePrueba.Modo.NO_RESPONDER);
        long inicio = System.currentTimeMillis();
        assertThatThrownBy(() -> notificador.enviarConAdjuntos(mensaje())).isInstanceOf(CorreoNoEnviadoException.class);
        assertThat(System.currentTimeMillis() - inicio).isLessThan(7000);
    }

    @Test
    void sinServidorDeCorreoConfiguradoNoFingeQueSalio() {
        NotificadorCorreoReal sinConfigurar = new NotificadorCorreoReal(new JavaMailSenderImpl(), "");
        assertThatThrownBy(() -> sinConfigurar.enviarConAdjuntos(mensaje()))
                .isInstanceOf(CorreoNoEnviadoException.class).hasMessageContaining("Falta configurar");
    }

    @Test
    void enLosRegistrosNoSeExponeElCorreoCompleto() {
        assertThat(NotificadorCorreo.ocultar("lisandro.madrid@gmail.com")).isEqualTo("l***@gmail.com");
        assertThat(NotificadorCorreo.ocultar("sinarroba")).isEqualTo("***");
    }
}
