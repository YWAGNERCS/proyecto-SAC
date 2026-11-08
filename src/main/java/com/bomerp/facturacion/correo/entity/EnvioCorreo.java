package com.bomerp.facturacion.correo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Una solicitud de entrega de un comprobante (factura o boleta) por correo al cliente, con el PDF y el XML firmado.
 * Cada intento queda además en {@link IntentoCorreo}: esta fila guarda el estado actual, aquella el historial.
 *
 * Estados:
 * - PENDIENTE: por enviar (recién creada).
 * - EN_PROCESO: un trabajador la tomó y está hablando con el servidor SMTP.
 * - ENVIADO: el servidor SMTP aceptó el mensaje (no garantiza que llegó a la bandeja del cliente).
 * - FALLIDO: no se envió; si queda proximoIntento, se reintenta sola a esa hora.
 * - INCIERTO: el proceso se interrumpió en medio del envío (p. ej. se apagó el backend) y no se sabe si el servidor
 *   SMTP llegó a aceptarlo. No se reintenta solo, para no mandar el comprobante dos veces: lo decide Lisandro.
 */
@Entity
@Table(name = "ENVIOS_CORREO")
@Getter
@Setter
@NoArgsConstructor
public class EnvioCorreo {

    public enum Tipo { AUTOMATICO, MANUAL }

    public enum Estado { PENDIENTE, EN_PROCESO, ENVIADO, FALLIDO, INCIERTO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "comprobante_id", nullable = false)
    private Long comprobanteId;

    /** A quién se envía: el correo del cliente al pedir el envío. */
    @Column(nullable = false, length = 200)
    private String destinatario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Tipo tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Estado estado = Estado.PENDIENTE;

    /** Quién pidió el reenvío manual (null en los automáticos). */
    @Column(name = "solicitado_por", length = 100)
    private String solicitadoPor;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    @Column(name = "fecha_ultimo_intento")
    private LocalDateTime fechaUltimoIntento;

    /** Cuándo lo aceptó el servidor SMTP. */
    @Column(name = "fecha_aceptacion_smtp")
    private LocalDateTime fechaAceptacionSmtp;

    @Column(nullable = false)
    private int intentos;

    @Column(name = "ultimo_error", length = 1000)
    private String ultimoError;

    /** Message-ID del último intento; se fija ANTES de enviar, así queda rastreable aunque el proceso se caiga. */
    @Column(name = "message_id", length = 250)
    private String messageId;

    /** Para un FALLIDO: cuándo se vuelve a intentar solo (null = ya no se reintenta solo). */
    @Column(name = "proximo_intento")
    private LocalDateTime proximoIntento;

    /** Desde cuándo está EN_PROCESO (si pasa demasiado, se interrumpió). */
    @Column(name = "en_proceso_desde")
    private LocalDateTime enProcesoDesde;

    public static EnvioCorreo nuevo(Long comprobanteId, String destinatario, Tipo tipo, String solicitadoPor) {
        EnvioCorreo e = new EnvioCorreo();
        e.comprobanteId = comprobanteId;
        e.destinatario = destinatario;
        e.tipo = tipo;
        e.solicitadoPor = solicitadoPor;
        return e;
    }

    public void setUltimoError(String error) {
        this.ultimoError = error == null ? null : (error.length() > 1000 ? error.substring(0, 1000) : error);
    }
}
