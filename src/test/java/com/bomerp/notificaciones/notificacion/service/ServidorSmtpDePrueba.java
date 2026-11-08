package com.bomerp.notificaciones.notificacion.service;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Servidor SMTP mínimo, solo para pruebas: escucha en 127.0.0.1 en un puerto libre y guarda cada mensaje que recibe.
 * Nunca reenvía nada: ningún correo sale de la PC. Puede rechazar los mensajes (550) o no responder (para probar
 * los plazos de espera).
 */
public class ServidorSmtpDePrueba implements AutoCloseable {

    public enum Modo { ACEPTAR, RECHAZAR, NO_RESPONDER }

    private final ServerSocket socket;
    private final Thread hilo;
    private volatile Modo modo = Modo.ACEPTAR;
    private final List<String> mensajes = new CopyOnWriteArrayList<>();

    public ServidorSmtpDePrueba() throws IOException {
        socket = new ServerSocket(0, 5, java.net.InetAddress.getLoopbackAddress());
        hilo = new Thread(this::atender, "smtp-de-prueba");
        hilo.setDaemon(true);
        hilo.start();
    }

    public int puerto() {
        return socket.getLocalPort();
    }

    public void modo(Modo m) {
        this.modo = m;
    }

    /** Cada mensaje recibido, tal como llegó (cabeceras y cuerpo MIME). */
    public List<String> mensajes() {
        return mensajes;
    }

    private void atender() {
        while (!socket.isClosed()) {
            try (Socket s = socket.accept()) {
                if (modo == Modo.NO_RESPONDER) {
                    Thread.sleep(8000);   // ni siquiera el saludo: el cliente debe rendirse por tiempo
                    continue;
                }
                conversar(s);
            } catch (Exception e) {
                // conexión cerrada o servidor apagado: se sigue con la siguiente
            }
        }
    }

    private void conversar(Socket s) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.ISO_8859_1));
        Writer out = new OutputStreamWriter(s.getOutputStream(), StandardCharsets.ISO_8859_1);
        responder(out, "220 smtp-de-prueba listo");
        String linea;
        while ((linea = in.readLine()) != null) {
            String cmd = linea.toUpperCase();
            if (cmd.startsWith("EHLO") || cmd.startsWith("HELO")) {
                responder(out, "250 smtp-de-prueba");
            } else if (cmd.startsWith("MAIL FROM") || cmd.startsWith("RSET") || cmd.startsWith("NOOP")) {
                responder(out, "250 OK");
            } else if (cmd.startsWith("RCPT TO")) {
                responder(out, modo == Modo.RECHAZAR ? "550 5.1.1 Buzon no disponible" : "250 OK");
            } else if (cmd.startsWith("DATA")) {
                responder(out, "354 Fin con <CRLF>.<CRLF>");
                StringBuilder sb = new StringBuilder();
                String l;
                while ((l = in.readLine()) != null && !l.equals(".")) {
                    sb.append(l.startsWith("..") ? l.substring(1) : l).append("\r\n");
                }
                mensajes.add(sb.toString());
                responder(out, "250 OK en cola");
            } else if (cmd.startsWith("QUIT")) {
                responder(out, "221 Adios");
                return;
            } else {
                responder(out, "502 No implementado");
            }
        }
    }

    private static void responder(Writer out, String texto) throws IOException {
        out.write(texto + "\r\n");
        out.flush();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
