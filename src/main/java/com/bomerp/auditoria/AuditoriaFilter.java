package com.bomerp.auditoria;

import com.bomerp.auditoria.service.AuditoriaService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Registra en el historial cada POST, PUT, PATCH o DELETE al panel (/api/v1/admin/**): quién, qué,
 * cuándo, desde qué IP y cómo terminó, incluidos los intentos sin permiso (403).
 *
 * Va dentro de la cadena de Spring Security, después de validar el token y antes de autorizar
 * (lo agrega SecurityConfig, no es un @Component para que no corra dos veces). Así conoce al usuario
 * y ve también el 403 que escribe el manejador de acceso denegado.
 */
public class AuditoriaFilter extends OncePerRequestFilter {

    private static final Set<String> METODOS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final AuditoriaService auditoriaService;
    private final ObjectMapper objectMapper;

    public AuditoriaFilter(AuditoriaService auditoriaService, ObjectMapper objectMapper) {
        this.auditoriaService = auditoriaService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !METODOS.contains(request.getMethod()) || !request.getRequestURI().startsWith("/api/v1/admin/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ContentCachingRequestWrapper peticion = new ContentCachingRequestWrapper(request, 8192);
        ContentCachingResponseWrapper respuesta = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(peticion, respuesta);
        } finally {
            try {
                registrar(peticion, respuesta);
            } finally {
                respuesta.copyBodyToResponse();
            }
        }
    }

    private void registrar(ContentCachingRequestWrapper peticion, ContentCachingResponseWrapper respuesta) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String usuario = auth != null && auth.isAuthenticated() ? auth.getName() : null;
        String roles = auth == null ? null : auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.equals("ROLE_ADMIN") || a.equals("ROLE_ASISTENTE") || a.startsWith("ROLE_PERM_"))
                .map(a -> a.substring("ROLE_".length()))
                .sorted()
                .collect(Collectors.joining(", "));
        int estado = respuesta.getStatus();
        JsonNode cuerpo = leer(respuesta.getContentAsByteArray());

        String idCreado = null;
        String detalle = null;
        if (estado >= 400) {
            // El mensaje que vio el usuario (GlobalExceptionHandler / SecurityConfig usan "message")
            detalle = cuerpo == null ? null : cuerpo.path("message").asString(null);
        } else if (cuerpo != null && cuerpo.has("id")) {
            idCreado = cuerpo.path("id").asString();
        }
        if (peticion.getRequestURI().startsWith("/api/v1/admin/permisos/") && estado < 400) {
            // Qué permisos quedaron: es lo que interesa revisar después
            JsonNode pedido = leer(peticion.getContentAsByteArray());
            if (pedido != null && pedido.path("permisos").isArray()) {
                StringBuilder sb = new StringBuilder();
                pedido.path("permisos").forEach(p -> sb.append(sb.isEmpty() ? "" : ", ").append(p.asString()));
                detalle = "Quedó con: " + (sb.isEmpty() ? "ningún permiso" : sb);
            }
        }
        auditoriaService.registrar(usuario, roles, peticion.getMethod(), peticion.getRequestURI(), estado,
                idCreado, detalle, peticion.getRemoteAddr());
    }

    private JsonNode leer(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            JsonNode nodo = objectMapper.readTree(new String(bytes, StandardCharsets.UTF_8));
            return nodo != null && nodo.isObject() ? nodo : null;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
