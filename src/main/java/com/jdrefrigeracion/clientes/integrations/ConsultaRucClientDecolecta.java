package com.jdrefrigeracion.clientes.integrations;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Nueva implementación del cliente RUC conectada a Decolecta.
 * Usamos @Primary para que Spring inyecte esta clase en lugar de apis.net.pe.
 */
@Component
@Primary
public class ConsultaRucClientDecolecta implements ConsultaRucClient {

    private final RestTemplate restTemplate;

    @Value("${decolecta.token}")
    private String token;

    public ConsultaRucClientDecolecta() {
        this.restTemplate = new RestTemplate();
    }

    @Override
    public DatosRuc consultar(String ruc) {
        String url = "https://api.decolecta.com/v1/sunat/ruc?numero=" + ruc;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/json");
        // Spring automáticamente añade "Bearer " al usar setBearerAuth
        headers.setBearerAuth(token);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, entity, JsonNode.class);
            JsonNode body = response.getBody();

            if (body != null) {
                // Al no conocer el JSON exacto de Decolecta, leemos de forma dinámica los campos más comunes
                String razonSocial = body.has("razonSocial") ? body.get("razonSocial").asText() :
                                     body.has("nombre") ? body.get("nombre").asText() : 
                                     body.has("razon_social") ? body.get("razon_social").asText() : "Sin Nombre";

                String direccion = body.has("direccion") ? body.get("direccion").asText() :
                                   body.has("domicilioFiscal") ? body.get("domicilioFiscal").asText() : 
                                   body.has("domicilio_fiscal") ? body.get("domicilio_fiscal").asText() : "Sin Dirección";

                return new DatosRuc(ruc, razonSocial, direccion);
            }
        } catch (Exception e) {
            System.err.println("Error al consultar RUC en Decolecta: " + e.getMessage());
        }
        return null;
    }

    @Override
    public DatosDni consultarDni(String dni) {
        // Como no tengo la URL de Decolecta para DNI, puedes implementar la llamada
        // similar a la de arriba, o seguir usando apis.net.pe aquí dentro.
        System.err.println("Consulta de DNI por Decolecta pendiente de endpoint.");
        return null;
    }
}
