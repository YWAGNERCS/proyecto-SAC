package com.bomerp.clientes.consultaruc.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;

/**
 * Proveedor de respaldo: Decolecta (sucesor de apis.net.pe), requiere token gratuito.
 *   RUC: https://api.decolecta.com/v1/sunat/ruc?numero=   (padrón SUNAT)
 *   DNI: https://api.decolecta.com/v1/reniec/dni?numero=  (RENIEC)
 * Solo se usa si hay token (decolecta.token / variable DECOLECTA_TOKEN); sin token no hace llamadas.
 */
@Component
@Slf4j
public class ConsultaRucClientDecolecta implements ConsultaRucClient {

    private static final String URL_BASE = "https://api.decolecta.com/v1";

    private final RestTemplate restTemplate;

    @Value("${decolecta.token:}")
    private String token;

    public ConsultaRucClientDecolecta() {
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
        JsonNode body = consultarApi("/sunat/ruc?numero=" + ruc);
        if (body == null) return null;
        String razonSocial = texto(body, "razon_social", "razonSocial", "nombre");
        if (razonSocial == null) return null;
        return new DatosRuc(ruc, razonSocial, texto(body, "direccion", "domicilio_fiscal", "domicilioFiscal"));
    }

    @Override
    public DatosDni consultarDni(String dni) {
        JsonNode body = consultarApi("/reniec/dni?numero=" + dni);
        if (body == null) return null;
        String nombres = texto(body, "first_name", "nombres");
        if (nombres == null) return null;
        return new DatosDni(dni, nombres,
                texto(body, "first_last_name", "apellido_paterno", "apellidoPaterno"),
                texto(body, "second_last_name", "apellido_materno", "apellidoMaterno"));
    }

    private JsonNode consultarApi(String ruta) {
        if (!estaConfigurado()) return null;
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/json");
        headers.setBearerAuth(token);
        try {
            return restTemplate.exchange(URL_BASE + ruta, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class).getBody();
        } catch (HttpClientErrorException.NotFound e) {
            log.info("Decolecta: sin resultados para {}", ruta);
        } catch (Exception e) {
            log.warn("Decolecta no respondió ({}): {}", ruta, e.getMessage());
        }
        return null;
    }

    /**
     * Lee el primer campo presente: el JSON de Decolecta usa snake_case.
     * Solo valores simples (texto, número): en Jackson 3 asString() lanza excepción sobre un objeto o arreglo.
     */
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
