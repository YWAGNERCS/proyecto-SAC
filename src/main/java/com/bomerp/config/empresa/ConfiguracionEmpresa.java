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
 * Datos dinámicos de contacto y cuentas bancarias de JD Refrigeración S.A.C.
 * que el administrador puede modificar desde la pantalla de Configuración (tabla CONFIGURACION_EMPRESA, fila única ID=1).
 * Si no existe fila en BD o algún campo es nulo, el sistema utiliza los valores por defecto de EmpresaProperties.
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

    /** Número de cuenta corriente BCP */
    @Column(name = "cuenta_bcp", length = 50)
    private String cuentaBcp;

    /** Código de Cuenta Interbancario (CCI) BCP */
    @Column(name = "cci_bcp", length = 50)
    private String cciBcp;

    /** Número de cuenta Banco de la Nación */
    @Column(name = "cuenta_nacion", length = 50)
    private String cuentaNacion;

    @Column(name = "actualizado_por", length = 100)
    private String actualizadoPor;

    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;
}
