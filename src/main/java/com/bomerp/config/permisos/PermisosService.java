package com.bomerp.config.permisos;

import com.bomerp.config.RecursoNoEncontradoException;
import com.bomerp.config.ReglaNegocioException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Permisos delegables por usuario (pantalla Configuración › Usuarios y permisos, solo ADMIN).
 *
 * Reglas: un ADMIN ya tiene todos (no se le editan); el administrador no se cambia los suyos;
 * solo se tocan los roles PERM_* (nunca ADMIN ni ASISTENTE, que siguen en la consola de Keycloak).
 * El cambio llega a la sesión del usuario cuando su token se renueva (5 minutos como máximo) o al
 * volver a iniciar sesión.
 */
@Service
@Slf4j
public class PermisosService {

    private final KeycloakAdminClient keycloak;

    public PermisosService(KeycloakAdminClient keycloak) {
        this.keycloak = keycloak;
    }

    public record PermisoDTO(String codigo, String nombre, String descripcion) {}

    public record UsuarioPermisosDTO(String username, String nombre, String email, boolean habilitado,
                                     String rol, List<String> permisos) {}

    public record PermisosDTO(List<PermisoDTO> catalogo, List<UsuarioPermisosDTO> usuarios) {}

    public PermisosDTO listar() {
        List<PermisoDTO> catalogo = Arrays.stream(Permiso.values())
                .map(p -> new PermisoDTO(p.name(), p.getNombre(), p.getDescripcion())).toList();
        List<UsuarioPermisosDTO> usuarios = keycloak.usuarios().stream()
                .map(u -> aDto(u, keycloak.rolesDe(u.path("id").asString())))
                .sorted(Comparator.comparing((UsuarioPermisosDTO u) -> !"ADMIN".equals(u.rol())).thenComparing(UsuarioPermisosDTO::username))
                .toList();
        return new PermisosDTO(catalogo, usuarios);
    }

    /**
     * Deja al usuario exactamente con los permisos indicados (agrega los que faltan y quita los demás).
     *
     * @param quien preferred_username del administrador que hace el cambio
     */
    public UsuarioPermisosDTO actualizar(String username, List<String> codigos, String quien) {
        if (username.equalsIgnoreCase(quien)) {
            throw new ReglaNegocioException("No puede cambiar sus propios permisos.");
        }
        JsonNode usuario = keycloak.usuarios().stream()
                .filter(u -> u.path("username").asString().equalsIgnoreCase(username))
                .findFirst()
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el usuario " + username + " en Keycloak."));
        String id = usuario.path("id").asString();
        List<String> roles = keycloak.rolesDe(id);
        if (roles.contains("ADMIN")) {
            throw new ReglaNegocioException(username + " es administrador: ya tiene todos los permisos.");
        }

        Set<String> pedidos = new LinkedHashSet<>();
        for (String codigo : codigos == null ? List.<String>of() : codigos) {
            Permiso p;
            try {
                p = Permiso.valueOf(codigo);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Permiso desconocido: " + codigo + ".");
            }
            pedidos.add(p.rol());
        }
        List<String> actuales = roles.stream().filter(r -> Permiso.deRol(r).isPresent()).toList();
        List<String> agregar = pedidos.stream().filter(r -> !actuales.contains(r)).toList();
        List<String> quitar = actuales.stream().filter(r -> !pedidos.contains(r)).toList();
        keycloak.asignarRoles(id, agregar);
        keycloak.quitarRoles(id, quitar);
        log.info("Permisos de {} cambiados por {}: +{} -{}", username, quien, agregar, quitar);
        return aDto(usuario, keycloak.rolesDe(id));
    }

    private static UsuarioPermisosDTO aDto(JsonNode u, List<String> roles) {
        String nombre = (u.path("firstName").asString("") + " " + u.path("lastName").asString("")).trim();
        String rol = roles.contains("ADMIN") ? "ADMIN" : roles.contains("ASISTENTE") ? "ASISTENTE" : "SIN_ROL";
        List<String> permisos = new ArrayList<>();
        for (String r : roles) {
            Permiso.deRol(r).ifPresent(p -> permisos.add(p.name()));
        }
        return new UsuarioPermisosDTO(u.path("username").asString(), nombre.isEmpty() ? u.path("username").asString() : nombre,
                u.path("email").asString(""), u.path("enabled").asBoolean(true), rol, permisos);
    }
}
