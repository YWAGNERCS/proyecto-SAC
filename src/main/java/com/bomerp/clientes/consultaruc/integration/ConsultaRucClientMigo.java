package com.bomerp.clientes.consultaruc.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;

/**
 * Cliente de integración con MIGO API (https://api.migo.pe).
 * Consulta RUC (SUNAT) y DNI (RENIEC) mediante la versión v2.
 * Requiere token configurado en migo.token o variable de entorno MIGO_TOKEN.
 */
@Component
@Slf4j
public class ConsultaRucClientMigo implements ConsultaRucClient {

    private static final String URL_BASE = "https://api.migo.pe/api/v2";

    private final RestTemplate restTemplate;

    @Value("${migo.token:}")
    private String token;

    public ConsultaRucClientMigo() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(factory);
    }

    public boolean estaConfigurado() {
        return token != null && !token.isBlank();
    }

    @Override
    public DatosRuc consultar(String ruc) {
        if (!estaConfigurado()) return null;
        JsonNode body = consultarApi("/ruc/" + ruc);
        if (body == null) return null;

        boolean success = body.path("success").asBoolean(true);
        if (!success) return null;

        String razonSocial = texto(body, "nombre_o_razon_social", "razon_social", "nombre");
        if (razonSocial == null) return null;

        String direccion = texto(body, "direccion", "domicilio_fiscal", "direccion_simple");
        return new DatosRuc(ruc, razonSocial, direccion);
    }

    @Override
    public DatosDni consultarDni(String dni) {
        if (!estaConfigurado()) return null;
        JsonNode body = consultarApi("/dni/" + dni);
        if (body == null) return null;

        boolean success = body.path("success").asBoolean(true);
        if (!success) return null;

        String apePat = texto(body, "apellido_paterno", "apellidoPaterno");
        String apeMat = texto(body, "apellido_materno", "apellidoMaterno");
        String nombres = texto(body, "nombres");
        String nombreCompleto = texto(body, "nombre");

        if (nombres == null && nombreCompleto != null) {
            String[] partes = nombreCompleto.trim().split("\\s+");
            if (partes.length == 1) {
                nombres = partes[0];
                apePat = "";
                apeMat = "";
            } else if (partes.length == 2) {
                apePat = partes[0];
                apeMat = "";
                nombres = partes[1];
            } else {
                apePat = partes[0];
                apeMat = partes[1];
                StringBuilder sb = new StringBuilder();
                for (int i = 2; i < partes.length; i++) {
                    if (sb.length() > 0) sb.append(" ");
                    sb.append(partes[i]);
                }
                nombres = sb.toString();
            }
        }

        if (nombres == null) return null;

        return new DatosDni(dni, nombres, apePat != null ? apePat : "", apeMat != null ? apeMat : "");
    }

    private JsonNode consultarApi(String ruta) {
        if (!estaConfigurado()) return null;
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/json");
        headers.setBearerAuth(token.trim());
        try {
            ResponseEntity<JsonNode> resp = restTemplate.exchange(
                    URL_BASE + ruta,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    JsonNode.class
            );
            return resp.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            log.info("MIGO API: sin resultados para {}", ruta);
            return null;
        } catch (Exception e) {
            log.warn("MIGO API no respondió ({}): {}", ruta, e.getMessage());
            throw new ProveedorDocumentosNoDisponibleException("MIGO API no disponible temporalmente: " + e.getMessage());
        }
    }

    private static String texto(JsonNode body, String... campos) {
        for (String campo : campos) {
            JsonNode valor = body.get(campo);
            if (valor != null && valor.isValueNode() && !valor.isNull() && !valor.asString().isBlank()) {
                return valor.asString().trim();
            }
        }
        return null;
    }
}
