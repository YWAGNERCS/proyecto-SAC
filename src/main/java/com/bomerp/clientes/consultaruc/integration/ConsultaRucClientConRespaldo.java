package com.bomerp.clientes.consultaruc.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Implementación que inyecta Spring en Clientes y Proveedores (@Primary).
 *
 * 1. Valida el formato (RUC de 11 dígitos, DNI de 8) → 400 si no cumple.
 * 2. Devuelve el resultado guardado si el número ya se consultó (evita el límite de apis.net.pe).
 * 3. Consulta apis.net.pe v1 (gratis, sin token).
 * 4. Si apis.net.pe no responde (429, tiempo agotado, error) o no encuentra el número,
 *    consulta Decolecta (solo si hay token).
 * 5. Si ningún proveedor pudo responder → 503 "intente en unos segundos" (no es lo mismo que "no existe").
 * Con app.consulta-documentos.simulado=true devuelve datos de prueba sin salir a internet.
 *
 * Principio D (SOLID): ClienteService y ProveedorService siguen dependiendo solo de la
 * interfaz ConsultaRucClient; cambiar o agregar proveedores no los toca.
 */
@Component
@Primary
@Slf4j
public class ConsultaRucClientConRespaldo implements ConsultaRucClient {

    private static final int MAX_CACHE = 500;

    private final ConsultaRucClientApisNetPe apisNetPe;
    private final ConsultaRucClientDecolecta decolecta;
    private final ConsultaRucClientSimulado simulado;
    private final boolean usarSimulado;

    // Solo resultados encontrados: un RUC o DNI no cambia de titular en minutos
    private final Map<String, DatosRuc> cacheRuc = new ConcurrentHashMap<>();
    private final Map<String, DatosDni> cacheDni = new ConcurrentHashMap<>();

    public ConsultaRucClientConRespaldo(ConsultaRucClientApisNetPe apisNetPe,
                                        ConsultaRucClientDecolecta decolecta,
                                        ConsultaRucClientSimulado simulado,
                                        @Value("${app.consulta-documentos.simulado:false}") boolean usarSimulado) {
        this.apisNetPe = apisNetPe;
        this.decolecta = decolecta;
        this.simulado = simulado;
        this.usarSimulado = usarSimulado;
        log.info("Consulta RUC/DNI: {}", usarSimulado ? "SIMULADA (sin internet)"
                : "apis.net.pe v1" + (decolecta.estaConfigurado() ? " + respaldo Decolecta" : " (sin respaldo: falta DECOLECTA_TOKEN)"));
    }

    @Override
    public DatosRuc consultar(String ruc) {
        if (ruc == null || !ruc.matches("\\d{11}")) {
            throw new IllegalArgumentException("El RUC debe tener 11 dígitos.");
        }
        if (usarSimulado) return simulado.consultar(ruc);
        return consultarConRespaldo("RUC", ruc, cacheRuc, apisNetPe::consultar, decolecta::consultar);
    }

    @Override
    public DatosDni consultarDni(String dni) {
        if (dni == null || !dni.matches("\\d{8}")) {
            throw new IllegalArgumentException("El DNI debe tener 8 dígitos.");
        }
        if (usarSimulado) return simulado.consultarDni(dni);
        return consultarConRespaldo("DNI", dni, cacheDni, apisNetPe::consultarDni, decolecta::consultarDni);
    }

    private <T> T consultarConRespaldo(String tipo, String numero, Map<String, T> cache,
                                       Function<String, T> principal, Function<String, T> respaldo) {
        T guardado = cache.get(numero);
        if (guardado != null) return guardado;

        boolean principalNoDisponible = false;
        T datos = null;
        try {
            datos = principal.apply(numero);
        } catch (ProveedorDocumentosNoDisponibleException e) {
            principalNoDisponible = true;
        }
        if (datos == null && decolecta.estaConfigurado()) {
            datos = respaldo.apply(numero);
        }

        if (datos != null) {
            if (cache.size() >= MAX_CACHE) cache.clear();
            cache.put(numero, datos);
            return datos;
        }
        if (principalNoDisponible && !decolecta.estaConfigurado()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "El servicio de consulta de " + tipo + " está ocupado. Intente de nuevo en unos segundos.");
        }
        return null;   // el número no existe → el servicio de Clientes/Proveedores responde 404
    }
}
