package com.bomerp.seguridad;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Perfil del usuario autenticado, leído directo del JWT ya validado (sin consultar ninguna base):
 * el primer paso para comparar un atributo del usuario (sub) con uno del recurso (vendedorId).
 */
@RestController
public class PerfilController {

    /** Roles del negocio; el token trae además roles propios de Keycloak que la app no usa. */
    private static final List<String> ROLES_SISTEMA = List.of("ADMIN", "ASISTENTE");

    @GetMapping("/api/v1/auth/perfil")
    public Map<String, Object> perfil(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        Object roles = realmAccess == null ? null : realmAccess.get("roles");
        List<String> todos = roles instanceof Collection<?> lista
                ? lista.stream().map(String::valueOf).toList()
                : List.of();

        Map<String, Object> perfil = new LinkedHashMap<>();
        perfil.put("sub", jwt.getSubject());
        perfil.put("username", jwt.getClaimAsString("preferred_username"));
        perfil.put("email", jwt.getClaimAsString("email"));
        perfil.put("nombre", jwt.getClaimAsString("name"));
        perfil.put("roles", todos.stream().filter(ROLES_SISTEMA::contains).toList());
        perfil.put("rolesToken", todos);
        perfil.put("emisor", jwt.getIssuer() != null ? jwt.getIssuer().toString() : null);
        perfil.put("expira", jwt.getExpiresAt());
        return perfil;
    }
}
