package com.bomerp.config.empresa;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ConfiguracionEmpresaService {

    private final ConfiguracionEmpresaRepository repository;
    private final EmpresaProperties props;

    public ConfiguracionEmpresaService(ConfiguracionEmpresaRepository repository, EmpresaProperties props) {
        this.repository = repository;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public ConfiguracionEmpresaDTO obtener() {
        return respuesta(repository.findById(ConfiguracionEmpresa.ID_UNICO).orElse(null));
    }

    @Transactional
    public ConfiguracionEmpresaDTO cambiarWhatsapp(String whatsapp, String usuario) {
        ConfiguracionEmpresa entidad = repository.findById(ConfiguracionEmpresa.ID_UNICO)
                .orElseGet(ConfiguracionEmpresa::new);

        entidad.setWhatsapp(limpiar(whatsapp));
        entidad.setActualizadoPor(usuario);
        entidad.setFechaActualizacion(LocalDateTime.now());

        return respuesta(repository.save(entidad));
    }

    private ConfiguracionEmpresaDTO respuesta(ConfiguracionEmpresa entidad) {
        return new ConfiguracionEmpresaDTO(
                limpiar(entidad != null ? entidad.getWhatsapp() : props.telefono()),
                limpiar(props.cuentaBcp()),
                limpiar(props.cciBcp()),
                limpiar(props.cuentaNacion()),
                limpiar(props.cuentaDetracciones()),
                entidad != null ? entidad.getActualizadoPor() : null,
                entidad != null ? entidad.getFechaActualizacion() : null
        );
    }

    private static String limpiar(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
