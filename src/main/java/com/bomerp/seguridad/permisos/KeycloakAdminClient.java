package com.bomerp.seguridad.permisos;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Cliente de la API de administración de Keycloak para el realm de SGE-JD: usuarios, sus roles de realm
 * (los permisos PERM_*) y los eventos de inicio de sesión.
 *
 * Se autentica con su propia cuenta de servicio (cliente confidencial "sgejd-backend", flujo
 * client_credentials, creado por keycloak/configurar_realm.py con los roles view-users, manage-users,
 * view-realm y view-events). Nunca usa la contraseña de un usuario. El secreto va en
 * application-local.properties o en la variable KC_BACKEND_SECRET, nunca en el repositorio.
 */
@Component
@Slf4j
public class KeycloakAdminClient {

    private final RestClient http = RestClient.create();
    private final String urlToken;
    private final String urlAdmin;
    private final String clientId;
    private final String clientSecret;

    private String token;
    private Instant vence = Instant.EPOCH;

    public KeycloakAdminClient(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
                               @Value("${app.keycloak.admin.client-id:sgejd-backend}") String clientId,
                               @Value("${app.keycloak.admin.client-secret:}") String clientSecret) {
        this.urlToken = issuer + "/protocol/openid-connect/token";
        // http://host/realms/bomerp → http://host/admin/realms/bomerp
        this.urlAdmin = issuer.replaceFirst("/realms/", "/admin/realms/");
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    /** Usuarios del realm (hasta 200: JD tiene pocos). */
    public List<JsonNode> usuarios() {
        return lista(get("/users?max=200&briefRepresentation=true"));
    }

    /** Roles de realm asignados directamente al usuario (ADMIN, ASISTENTE, PERM_*...). */
    public List<String> rolesDe(String usuarioId) {
        List<String> roles = new ArrayList<>();
        for (JsonNode r : lista(get("/users/" + usuarioId + "/role-mappings/realm"))) {
            roles.add(r.path("name").asString());
        }
        return roles;
    }

    public void asignarRoles(String usuarioId, List<String> roles) {
        if (!roles.isEmpty()) {
            enviar("POST", "/users/" + usuarioId + "/role-mappings/realm", representaciones(roles));
        }
    }

    public void quitarRoles(String usuarioId, List<String> roles) {
        if (!roles.isEmpty()) {
            enviar("DELETE", "/users/" + usuarioId + "/role-mappings/realm", representaciones(roles));
        }
    }

    /** Eventos de sesión (inicios, intentos fallidos y cierres), los más recientes primero. */
    public List<JsonNode> eventosDeSesion(int max) {
        return lista(get("/events?type=LOGIN&type=LOGIN_ERROR&type=LOGOUT&max=" + max));
    }

    // ── HTTP ──────────────────────────────────────────────────────

    private List<Map<String, Object>> representaciones(List<String> roles) {
        List<Map<String, Object>> salida = new ArrayList<>();
        for (String nombre : roles) {
            JsonNode rol = get("/roles/" + nombre);
            salida.add(Map.of("id", rol.path("id").asString(), "name", nombre));
        }
        return salida;
    }

    private JsonNode get(String ruta) {
        try {
            return http.get().uri(urlAdmin + ruta).header("Authorization", "Bearer " + token())
                    .retrieve().body(JsonNode.class);
        } catch (RestClientException e) {
            throw noDisponible(e);
        }
    }

    private void enviar(String metodo, String ruta, Object cuerpo) {
        try {
            http.method(org.springframework.http.HttpMethod.valueOf(metodo)).uri(urlAdmin + ruta)
                    .header("Authorization", "Bearer " + token())
                    .contentType(MediaType.APPLICATION_JSON).body(cuerpo)
                    .retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            throw noDisponible(e);
        }
    }

    private synchronized String token() {
        if (clientSecret == null || clientSecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Falta configurar el acceso a Keycloak (app.keycloak.admin.client-secret / KC_BACKEND_SECRET).");
        }
        if (token != null && Instant.now().isBefore(vence)) {
            return token;
        }
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        try {
            JsonNode r = http.post().uri(urlToken).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                    .retrieve().body(JsonNode.class);
            token = r.path("access_token").asString();
            // Se renueva 30 s antes de que venza
            vence = Instant.now().plusSeconds(Math.max(30, r.path("expires_in").asLong(60) - 30));
            return token;
        } catch (RestClientException e) {
            throw noDisponible(e);
        }
    }

    private static List<JsonNode> lista(JsonNode nodo) {
        List<JsonNode> salida = new ArrayList<>();
        if (nodo != null && nodo.isArray()) {
            nodo.forEach(salida::add);
        }
        return salida;
    }

    private static ResponseStatusException noDisponible(RestClientException e) {
        log.warn("Keycloak (administración) no respondió: {}", e.getMessage());
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "No se pudo consultar Keycloak. Revise que esté encendido e inténtelo de nuevo.");
    }
}
