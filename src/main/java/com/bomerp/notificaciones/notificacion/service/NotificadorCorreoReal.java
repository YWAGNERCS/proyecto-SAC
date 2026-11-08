package com.bomerp.notificaciones.notificacion.service;

import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class NotificadorCorreoReal implements NotificadorCorreo {

    private final JavaMailSender mailSender;

    @org.springframework.beans.factory.annotation.Value("${spring.mail.username}")
    private String remitente;

    @org.springframework.beans.factory.annotation.Autowired
    public NotificadorCorreoReal(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /** Para pruebas: con remitente fijo, sin leer la configuración. */
    NotificadorCorreoReal(JavaMailSender mailSender, String remitente) {
        this.mailSender = mailSender;
        this.remitente = remitente;
    }

    @Override
    public boolean enviarCorreo(String destinatario, String asunto, String cuerpo) {
        return enviarCorreoConAdjunto(destinatario, asunto, cuerpo, null);
    }

    @Override
    public boolean enviarCorreoConAdjunto(String destinatario, String asunto, String cuerpo, java.io.File adjunto) {
        if (remitente == null || remitente.isBlank()) {
            log.warn("Correo no enviado a {}: falta configurar MAIL_USERNAME / MAIL_PASSWORD.", NotificadorCorreo.ocultar(destinatario));
            return false;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            // true indica que será multipart (soporta HTML e imágenes incrustadas)
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(remitente);
            helper.setTo(destinatario);
            helper.setSubject(asunto);
            helper.setText(cuerpo, true); // true = el cuerpo es HTML

            incrustarLogo(helper);

            // Adjuntar archivo si existe
            if (adjunto != null && adjunto.exists()) {
                helper.addAttachment(adjunto.getName(), adjunto);
            }

            mailSender.send(message);
            log.info("Correo enviado a {}", NotificadorCorreo.ocultar(destinatario));
            return true;
        } catch (Exception e) {
            log.error("Error enviando correo a {}: {}", NotificadorCorreo.ocultar(destinatario), e.getMessage());
            return false;
        }
    }

    /**
     * Correo del comprobante (y cualquier otro con varios adjuntos). Los adjuntos van con sus bytes exactos (el XML
     * firmado no se toca) y el Message-ID propio se conserva al enviar, para poder rastrear el mensaje.
     */
    @Override
    public String enviarConAdjuntos(MensajeCorreo m) {
        if (remitente == null || remitente.isBlank()) {
            throw new CorreoNoEnviadoException("Falta configurar el servidor de correo (MAIL_USERNAME y MAIL_PASSWORD).");
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, "UTF-8");
            helper.setFrom(remitente);
            helper.setTo(m.destinatario());
            helper.setSubject(m.asunto());
            helper.setText(m.html(), true);
            incrustarLogo(helper);
            for (Adjunto a : m.adjuntos()) {
                helper.getRootMimeMultipart().addBodyPart(parteBase64(a));
            }
            if (m.messageId() != null) {
                // JavaMailSenderImpl respeta el Message-ID que ya trae el mensaje
                message.setHeader("Message-ID", m.messageId());
            }
            mailSender.send(message);
            String id = message.getMessageID();
            log.info("Correo con {} adjunto(s) aceptado por el servidor SMTP para {} ({})", m.adjuntos().size(),
                    NotificadorCorreo.ocultar(m.destinatario()), id);
            return id != null ? id : m.messageId();
        } catch (CorreoNoEnviadoException e) {
            throw e;
        } catch (Exception e) {
            String motivo = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.warn("El servidor SMTP no aceptó el correo para {}: {}", NotificadorCorreo.ocultar(m.destinatario()), motivo);
            throw new CorreoNoEnviadoException("El servidor de correo no aceptó el mensaje: " + motivo, e);
        }
    }

    /**
     * Adjunto siempre en base64: si JavaMail lo mandara como texto (7bit o quoted-printable, lo que elige cuando el
     * archivo parece texto, como un XML) cambiaría los saltos de línea a CRLF y el XML firmado ya no sería el mismo.
     */
    private static jakarta.mail.internet.MimeBodyPart parteBase64(Adjunto a) throws jakarta.mail.MessagingException {
        jakarta.mail.internet.MimeBodyPart parte = new jakarta.mail.internet.MimeBodyPart();
        parte.setDataHandler(new jakarta.activation.DataHandler(
                new jakarta.mail.util.ByteArrayDataSource(a.contenido(), a.tipoMime())));
        parte.setDisposition(jakarta.mail.Part.ATTACHMENT);
        try {
            parte.setFileName(jakarta.mail.internet.MimeUtility.encodeText(a.nombre(), "UTF-8", null));
        } catch (java.io.UnsupportedEncodingException e) {
            parte.setFileName(a.nombre());
        }
        parte.setHeader("Content-Transfer-Encoding", "base64");
        return parte;
    }

    /** Logo de JD incrustado (cid:logo): se ve sin descargar imágenes remotas. */
    private static void incrustarLogo(MimeMessageHelper helper) throws jakarta.mail.MessagingException {
        ClassPathResource logoImage = new ClassPathResource("static/images/logo.png");
        if (logoImage.exists()) {
            helper.addInline("logo", logoImage);
        }
    }
}
