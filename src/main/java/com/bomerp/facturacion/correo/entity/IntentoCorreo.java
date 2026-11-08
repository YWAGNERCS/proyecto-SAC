package com.bomerp.facturacion.correo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Un intento de entrega de {@link EnvioCorreo}: solo se agrega, nunca se modifica (historial auditable). */
@Entity
@Table(name = "INTENTOS_CORREO")
@Getter
@Setter
@NoArgsConstructor
public class IntentoCorreo {

    public enum Resultado { ENVIADO, FALLIDO, INCIERTO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "envio_correo_id", nullable = false)
    private Long envioCorreoId;

    @Column(nullable = false)
    private int intento;

    @Column(nullable = false)
    private LocalDateTime inicio;

    private LocalDateTime fin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Resultado resultado;

    @Column(length = 1000)
    private String mensaje;

    @Column(name = "message_id", length = 250)
    private String messageId;

    /** Qué se adjuntó: nombre, tamaño y huella SHA-256 de cada archivo (para comprobar después qué se mandó). */
    @Column(length = 600)
    private String adjuntos;

    public static IntentoCorreo de(EnvioCorreo envio, LocalDateTime inicio, Resultado resultado, String mensaje, String adjuntos) {
        IntentoCorreo i = new IntentoCorreo();
        i.envioCorreoId = envio.getId();
        i.intento = envio.getIntentos();
        i.inicio = inicio;
        i.fin = LocalDateTime.now();
        i.resultado = resultado;
        i.mensaje = mensaje == null ? null : (mensaje.length() > 1000 ? mensaje.substring(0, 1000) : mensaje);
        i.messageId = envio.getMessageId();
        i.adjuntos = adjuntos == null ? null : (adjuntos.length() > 600 ? adjuntos.substring(0, 600) : adjuntos);
        return i;
    }
}
