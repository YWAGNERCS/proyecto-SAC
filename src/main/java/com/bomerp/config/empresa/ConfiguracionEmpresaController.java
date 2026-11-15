package com.bomerp.config.empresa;

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
    public ConfiguracionEmpresaDTO cambiar(@RequestBody ConfiguracionEmpresaDTO pedido, @AuthenticationPrincipal Jwt jwt) {
        String usuario = jwt != null ? jwt.getClaimAsString("preferred_username") : "admin";
        if (usuario == null && jwt != null) usuario = jwt.getSubject();
        return service.cambiar(pedido, usuario != null ? usuario : "admin");
    }
}
