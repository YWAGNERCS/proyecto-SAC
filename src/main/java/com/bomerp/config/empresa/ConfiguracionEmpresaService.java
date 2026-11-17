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
        return repository.findById(ConfiguracionEmpresa.ID_UNICO)
                .map(c -> new ConfiguracionEmpresaDTO(
                        limpiar(c.getWhatsapp()),
                        limpiar(c.getCuentaBcp()),
                        limpiar(c.getCciBcp()),
                        limpiar(c.getCuentaNacion()),
                        c.getActualizadoPor(),
                        c.getFechaActualizacion()
                ))
                .orElseGet(() -> new ConfiguracionEmpresaDTO(
                        limpiar(props.telefono()),
                        limpiar(props.cuentaBcp()),
                        limpiar(props.cciBcp()),
                        limpiar(props.cuentaNacion()),
                        null,
                        null
                ));
    }

    @Transactional
    public ConfiguracionEmpresaDTO cambiar(ConfiguracionEmpresaDTO pedido, String usuario) {
        ConfiguracionEmpresa entidad = repository.findById(ConfiguracionEmpresa.ID_UNICO)
                .orElseGet(ConfiguracionEmpresa::new);

        entidad.setWhatsapp(limpiar(pedido.whatsapp()));
        entidad.setCuentaBcp(limpiar(pedido.cuentaBcp()));
        entidad.setCciBcp(limpiar(pedido.cciBcp()));
        entidad.setCuentaNacion(limpiar(pedido.cuentaNacion()));
        entidad.setActualizadoPor(usuario);
        entidad.setFechaActualizacion(LocalDateTime.now());

        repository.save(entidad);

        return new ConfiguracionEmpresaDTO(
                entidad.getWhatsapp(),
                entidad.getCuentaBcp(),
                entidad.getCciBcp(),
                entidad.getCuentaNacion(),
                entidad.getActualizadoPor(),
                entidad.getFechaActualizacion()
        );
    }

    private static String limpiar(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
