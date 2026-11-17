package com.bomerp.config.empresa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * WhatsApp de JD Refrigeración, editable por el administrador (fila única ID=1).
 * Si no existe fila en BD, se usa el teléfono de EmpresaProperties.
 */
@Entity
@Table(name = "CONFIGURACION_EMPRESA")
@Getter
@Setter
@NoArgsConstructor
public class ConfiguracionEmpresa {

    public static final long ID_UNICO = 1L;

    @Id
    private Long id = ID_UNICO;

    /** Celular / WhatsApp de atención y aprobación de proformas */
    @Column(name = "whatsapp", length = 30)
    private String whatsapp;

    @Column(name = "actualizado_por", length = 100)
    private String actualizadoPor;

    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;
}
