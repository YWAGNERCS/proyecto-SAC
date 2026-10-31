package com.bomerp.seguridad.auditoria.controller;

import com.bomerp.seguridad.auditoria.entity.RegistroCambio;
import com.bomerp.seguridad.auditoria.service.AuditoriaService;
import com.bomerp.seguridad.permisos.KeycloakAdminClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Historial de cambios e inicios de sesión. Solo ADMIN (regla /api/v1/admin/auditoria/** de SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin/auditoria")
public class AuditoriaController {

    private final AuditoriaService auditoriaService;
    private final KeycloakAdminClient keycloak;

    public AuditoriaController(AuditoriaService auditoriaService, KeycloakAdminClient keycloak) {
        this.auditoriaService = auditoriaService;
        this.keycloak = keycloak;
    }

    public record CambioDTO(Long id, LocalDateTime fechaHora, String usuario, String roles, String metodo, String ruta,
                            Integer estadoHttp, String resultado, String modulo, String accion, String detalle, String ip) {}

    public record HistorialDTO(List<CambioDTO> registros, boolean truncado, List<String> usuarios, List<String> modulos) {}

    /** Cambios, los más recientes primero (máximo 500 por consulta). */
    @GetMapping
    public HistorialDTO cambios(@RequestParam(required = false) String usuario,
                                @RequestParam(required = false) String modulo,
                                @RequestParam(required = false) RegistroCambio.Resultado resultado,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        List<CambioDTO> registros = auditoriaService.buscar(usuario, modulo, resultado, desde, hasta).stream()
                .map(r -> new CambioDTO(r.getId(), r.getFechaHora(), r.getUsuario(), r.getRoles(), r.getMetodo(),
                        r.getRuta(), r.getEstadoHttp(), r.getResultado().name(), r.getModulo(), r.getAccion(),
                        r.getDetalle(), r.getIp()))
                .toList();
        return new HistorialDTO(registros, registros.size() >= AuditoriaService.MAXIMO,
                auditoriaService.usuarios(), auditoriaService.modulos());
    }

    public record SesionDTO(LocalDateTime fechaHora, String tipo, String usuario, String ip, String error) {}

    /** Inicios de sesión, intentos fallidos y cierres que guarda Keycloak (los 200 más recientes). */
    @GetMapping("/sesiones")
    public List<SesionDTO> sesiones() {
        // Los cierres de sesión no traen el nombre de usuario, solo su id
        Map<String, String> nombres = new HashMap<>();
        keycloak.usuarios().forEach(u -> nombres.put(u.path("id").asString(), u.path("username").asString()));
        return keycloak.eventosDeSesion(200).stream().map(e -> aSesion(e, nombres)).toList();
    }

    private static SesionDTO aSesion(JsonNode e, Map<String, String> nombres) {
        LocalDateTime fecha = LocalDateTime.ofInstant(Instant.ofEpochMilli(e.path("time").asLong()), ZoneId.systemDefault());
        JsonNode det = e.path("details");
        String usuario = det.path("username").asString(null);
        if (usuario == null || usuario.isBlank()) {
            usuario = nombres.getOrDefault(e.path("userId").asString(""), "—");
        }
        return new SesionDTO(fecha, e.path("type").asString(), usuario, e.path("ipAddress").asString(null),
                e.path("error").asString(null));
    }
}
