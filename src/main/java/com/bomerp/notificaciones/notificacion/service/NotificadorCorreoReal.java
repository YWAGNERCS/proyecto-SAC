package com.bomerp.notificaciones.notificacion.service;

import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class NotificadorCorreoReal implements NotificadorCorreo {

    private final JavaMailSender mailSender;

    @org.springframework.beans.factory.annotation.Value("${spring.mail.username}")
    private String remitente;

    public NotificadorCorreoReal(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public boolean enviarCorreo(String destinatario, String asunto, String cuerpo) {
        return enviarCorreoConAdjunto(destinatario, asunto, cuerpo, null);
    }

    @Override
    public boolean enviarCorreoConAdjunto(String destinatario, String asunto, String cuerpo, java.io.File adjunto) {
        if (remitente == null || remitente.isBlank()) {
            System.err.println("Correo no enviado a " + destinatario + ": falta configurar MAIL_USERNAME / MAIL_PASSWORD.");
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
            
            // Incrustar el logo si existe
            ClassPathResource logoImage = new ClassPathResource("static/images/logo.png");
            if (logoImage.exists()) {
                helper.addInline("logo", logoImage);
            }
            
            // Adjuntar archivo si existe
            if (adjunto != null && adjunto.exists()) {
                helper.addAttachment(adjunto.getName(), adjunto);
            }
            
            mailSender.send(message);
            System.out.println("[REAL - Correo HTML] Enviado exitosamente a " + destinatario);
            return true;
        } catch (Exception e) {
            System.err.println("Error enviando correo HTML: " + e.getMessage());
            return false;
        }
    }
}
