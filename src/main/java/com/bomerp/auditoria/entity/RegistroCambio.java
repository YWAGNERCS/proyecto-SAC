package com.bomerp.auditoria.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Una acción que intentó cambiar algo en SGE-JD (POST, PUT, PATCH o DELETE del panel), con quién,
 * cuándo, desde dónde y cómo terminó: EXITO, DENEGADO (403: sin permiso), RECHAZADO (validación o
 * regla de negocio) o ERROR (falla del servidor). Las consultas (GET) no se registran.
 * Lo escribe AuditoriaFilter; solo se agrega, nunca se edita ni se borra desde la aplicación.
 */
@Entity
@Table(name = "HISTORIAL_CAMBIOS")
@Getter
@Setter
@NoArgsConstructor
public class RegistroCambio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    @Column(nullable = false, length = 100)
    private String usuario;

    /** Rol y permisos del usuario en ese momento (ADMIN, ASISTENTE, PERM_...). */
    @Column(length = 300)
    private String roles;

    @Column(nullable = false, length = 10)
    private String metodo;

    @Column(nullable = false, length = 300)
    private String ruta;

    @Column(name = "estado_http", nullable = false)
    private Integer estadoHttp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Resultado resultado;

    @Column(nullable = false, length = 40)
    private String modulo;

    /** Qué se hizo, en palabras ("Anuló la venta #113"). */
    @Column(nullable = false, length = 300)
    private String accion;

    /** Motivo del rechazo o del error (el mensaje que vio el usuario). */
    @Column(length = 500)
    private String detalle;

    @Column(length = 64)
    private String ip;

    public enum Resultado {
        EXITO, DENEGADO, RECHAZADO, ERROR
    }
}
