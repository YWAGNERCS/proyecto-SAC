package com.bomerp.clientes.consultaruc.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * Proveedor principal: API pública v1 de apis.net.pe (no requiere token).
 *   RUC: https://api.apis.net.pe/v1/ruc?numero=XXXXXXXXXXX  (padrón SUNAT)
 *   DNI: https://api.apis.net.pe/v1/dni?numero=XXXXXXXX     (datos RENIEC)
 * La v1 es la versión antigua y gratuita del servicio (hoy Decolecta). Tiene un límite
 * de consultas (responde 429 si se consulta varias veces seguidas): en ese caso lanza
 * ProveedorDocumentosNoDisponibleException y ConsultaRucClientConRespaldo pasa al respaldo.
 */
@Component
@Slf4j
public class ConsultaRucClientApisNetPe implements ConsultaRucClient {

    private static final String URL_BASE = "https://api.apis.net.pe/v1";

    private final RestTemplate restTemplate;

    public ConsultaRucClientApisNetPe() {
        // Tiempos máximos: una API externa lenta no debe dejar colgada la pantalla del usuario
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public DatosRuc consultar(String ruc) {
        try {
            ApisNetPeRucResponse r = restTemplate.getForObject(URL_BASE + "/ruc?numero=" + ruc, ApisNetPeRucResponse.class);
            if (r != null && r.nombre() != null) {
                return new DatosRuc(r.numeroDocumento(), r.nombre().trim(), r.direccion() != null ? r.direccion().trim() : null);
            }
        } catch (HttpClientErrorException.NotFound e) {
            log.info("apis.net.pe: RUC {} no encontrado", ruc);
        } catch (Exception e) {
            throw noDisponible("RUC", ruc, e);
        }
        return null;
    }

    @Override
    public DatosDni consultarDni(String dni) {
        try {
            ApisNetPeDniResponse r = restTemplate.getForObject(URL_BASE + "/dni?numero=" + dni, ApisNetPeDniResponse.class);
            if (r != null && r.nombres() != null) {
                return new DatosDni(r.numeroDocumento(), r.nombres(), r.apellidoPaterno(), r.apellidoMaterno());
            }
        } catch (HttpClientErrorException.NotFound e) {
            log.info("apis.net.pe: DNI {} no encontrado", dni);
        } catch (Exception e) {
            throw noDisponible("DNI", dni, e);
        }
        return null;
    }

    /** 429 (límite de consultas), tiempo agotado o error 5xx: el número podría existir, el servicio no respondió. */
    private ProveedorDocumentosNoDisponibleException noDisponible(String tipo, String numero, Exception e) {
        String causa = e instanceof HttpClientErrorException.TooManyRequests
                ? "límite de consultas alcanzado (429)"
                : e.getClass().getSimpleName() + ": " + e.getMessage();
        log.warn("apis.net.pe no respondió para el {} {}: {}", tipo, numero, causa);
        return new ProveedorDocumentosNoDisponibleException("apis.net.pe: " + causa);
    }

    // Records locales para mapear el JSON de la API
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ApisNetPeRucResponse(String nombre, String numeroDocumento, String direccion) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ApisNetPeDniResponse(String nombres, String apellidoPaterno, String apellidoMaterno, String numeroDocumento) {}
}
