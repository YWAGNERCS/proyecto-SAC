package com.bomerp.config.empresa;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/empresa/configuracion")
public class ConfiguracionEmpresaController {

    private final ConfiguracionEmpresaService service;

    public ConfiguracionEmpresaController(ConfiguracionEmpresaService service) {
        this.service = service;
    }

    @GetMapping
    public ConfiguracionEmpresaDTO obtener() {
        return service.obtener();
    }

    @PutMapping
    public ConfiguracionEmpresaDTO cambiar(@RequestBody CambioWhatsapp pedido, @AuthenticationPrincipal Jwt jwt) {
        String usuario = jwt != null ? jwt.getClaimAsString("preferred_username") : "admin";
        if (usuario == null && jwt != null) usuario = jwt.getSubject();
        return service.cambiarWhatsapp(pedido.whatsapp(), usuario != null ? usuario : "admin");
    }

    /** Los clientes anteriores pueden seguir enviando sus campos adicionales. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CambioWhatsapp(@JsonProperty(required = true) String whatsapp) {}
}
