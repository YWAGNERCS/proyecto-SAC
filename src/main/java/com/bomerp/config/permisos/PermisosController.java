package com.bomerp.config.permisos;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Usuarios y sus permisos delegables. Solo ADMIN (regla /api/v1/admin/permisos/** de SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin/permisos")
public class PermisosController {

    private final PermisosService permisosService;

    public PermisosController(PermisosService permisosService) {
        this.permisosService = permisosService;
    }

    /** Catálogo de permisos y cada usuario con su rol y sus permisos. */
    @GetMapping
    public PermisosService.PermisosDTO listar() {
        return permisosService.listar();
    }

    public record CambioPermisos(List<String> permisos) {}

    /** Deja al usuario con exactamente esos permisos (códigos de Permiso, p. ej. ANULAR_VENTAS). */
    @PutMapping("/{username}")
    public PermisosService.UsuarioPermisosDTO actualizar(@PathVariable String username, @RequestBody CambioPermisos cambio,
                                                         @AuthenticationPrincipal Jwt jwt) {
        return permisosService.actualizar(username, cambio.permisos(), jwt.getClaimAsString("preferred_username"));
    }
}
