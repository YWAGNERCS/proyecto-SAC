package com.bomerp.config.empresa;

import com.bomerp.seguridad.acceso.DetalleHistorial;
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
                        valorO(c.getWhatsapp(), props.telefono(), "912345678"),
                        valorO(c.getCuentaBcp(), props.cuentaBcp(), "193-12345678-0-12"),
                        valorO(c.getCciBcp(), props.cciBcp(), "002-193-001234567890-12"),
                        valorO(c.getCuentaNacion(), props.cuentaNacion(), "00-012-345678"),
                        c.getActualizadoPor(),
                        c.getFechaActualizacion()
                ))
                .orElseGet(() -> new ConfiguracionEmpresaDTO(
                        props.telefono() != null && !props.telefono().isBlank() ? props.telefono() : "912345678",
                        props.cuentaBcp() != null && !props.cuentaBcp().isBlank() ? props.cuentaBcp() : "193-12345678-0-12",
                        props.cciBcp() != null && !props.cciBcp().isBlank() ? props.cciBcp() : "002-193-001234567890-12",
                        props.cuentaNacion() != null && !props.cuentaNacion().isBlank() ? props.cuentaNacion() : "00-012-345678",
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

        DetalleHistorial.anotar("Actualizó cuentas bancarias y WhatsApp de la empresa (WhatsApp: "
                + entidad.getWhatsapp() + " · BCP: " + entidad.getCuentaBcp() + ")");

        return new ConfiguracionEmpresaDTO(
                entidad.getWhatsapp(),
                entidad.getCuentaBcp(),
                entidad.getCciBcp(),
                entidad.getCuentaNacion(),
                entidad.getActualizadoPor(),
                entidad.getFechaActualizacion()
        );
    }

    private static String valorO(String val, String fallback, String porDefecto) {
        if (val != null && !val.isBlank()) return val.trim();
        if (fallback != null && !fallback.isBlank()) return fallback.trim();
        return porDefecto;
    }

    private static String limpiar(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
