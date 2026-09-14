package com.jdrefrigeracion.clientes.integrations;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;

@Component
public class ConsultaRucClientApisNetPe implements ConsultaRucClient {

    private final RestTemplate restTemplate;

    public ConsultaRucClientApisNetPe() {
        this.restTemplate = new RestTemplate();
    }

    @Override
    public DatosRuc consultar(String ruc) {
        String url = "https://api.apis.net.pe/v1/ruc?numero=" + ruc;
        try {
            ApisNetPeRucResponse response = restTemplate.getForObject(url, ApisNetPeRucResponse.class);
            if (response != null && response.nombre() != null) {
                return new DatosRuc(response.numeroDocumento(), response.nombre(), response.direccion());
            }
        } catch (Exception e) {
            System.err.println("Error al consultar RUC en apis.net.pe: " + e.getMessage());
        }
        return null;
    }

    @Override
    public DatosDni consultarDni(String dni) {
        String url = "https://api.apis.net.pe/v1/dni?numero=" + dni;
        try {
            ApisNetPeDniResponse response = restTemplate.getForObject(url, ApisNetPeDniResponse.class);
            if (response != null && response.nombres() != null) {
                return new DatosDni(response.numeroDocumento(), response.nombres(), response.apellidoPaterno(), response.apellidoMaterno());
            }
        } catch (Exception e) {
            System.err.println("Error al consultar DNI en apis.net.pe: " + e.getMessage());
        }
        return null;
    }

    // Records locales para mapear el JSON de la API
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private record ApisNetPeRucResponse(String nombre, String numeroDocumento, String direccion) {}

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private record ApisNetPeDniResponse(String nombres, String apellidoPaterno, String apellidoMaterno, String numeroDocumento) {}
}
