package com.bomerp.config.tipocambio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tipo de cambio oficial SUNAT (soles por dólar) y conversión de precios entre monedas.
 *
 * Fuente: https://api.apis.net.pe/v1/tipo-cambio-sunat?fecha=AAAA-MM-DD (gratis, sin token).
 * Se guarda en memoria por fecha: SUNAT publica un valor por día y así se hace una sola consulta
 * diaria (apis.net.pe limita las consultas seguidas, igual que en RUC/DNI).
 *
 * Para vender se usa el tipo de cambio "venta" (el que aplica quien entrega dólares).
 * El vendedor puede escribir otro si lo negoció con el cliente.
 */
@Service
@Slf4j
public class TipoCambioService {

    private static final String URL = "https://api.apis.net.pe/v1/tipo-cambio-sunat?fecha=";
    private static final BigDecimal MINIMO = new BigDecimal("1.0");
    private static final BigDecimal MAXIMO = new BigDecimal("10.0");

    private final RestTemplate restTemplate;
    private final Map<LocalDate, TipoCambio> cache = new ConcurrentHashMap<>();

    public TipoCambioService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(factory);
    }

    public record TipoCambio(LocalDate fecha, BigDecimal compra, BigDecimal venta, String origen) {}

    /** Tipo de cambio SUNAT de una fecha (hoy si es null). 503 si el servicio no responde. */
    public TipoCambio obtener(LocalDate fecha) {
        LocalDate dia = fecha != null ? fecha : LocalDate.now();
        TipoCambio guardado = cache.get(dia);
        if (guardado != null) return guardado;
        try {
            RespuestaSunat r = restTemplate.getForObject(URL + dia, RespuestaSunat.class);
            if (r == null || r.venta() == null) {
                throw new IllegalStateException("respuesta vacía");
            }
            TipoCambio tc = new TipoCambio(r.fecha() != null ? r.fecha() : dia, r.compra(), r.venta(), "SUNAT");
            cache.put(dia, tc);
            return tc;
        } catch (Exception e) {
            log.warn("No se pudo obtener el tipo de cambio SUNAT del {}: {}", dia, e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo obtener el tipo de cambio de SUNAT. Ingréselo a mano o intente en unos segundos.");
        }
    }

    /**
     * Tipo de cambio a usar en una operación: el que escribió el vendedor (validado) o,
     * si no escribió ninguno, el de venta SUNAT de hoy.
     */
    public BigDecimal resolver(BigDecimal tipoCambioManual) {
        if (tipoCambioManual != null) {
            if (tipoCambioManual.compareTo(MINIMO) < 0 || tipoCambioManual.compareTo(MAXIMO) > 0) {
                throw new IllegalArgumentException("El tipo de cambio debe estar entre 1 y 10 soles por dólar.");
            }
            return tipoCambioManual.setScale(4, RoundingMode.HALF_UP);
        }
        return obtener(null).venta();
    }

    /** Como resolver(), pero devuelve null en vez de fallar si SUNAT no responde (para guardarlo solo como referencia). */
    public BigDecimal resolverSiSePuede(BigDecimal tipoCambioManual) {
        try {
            return resolver(tipoCambioManual);
        } catch (ResponseStatusException e) {
            return null;
        }
    }

    /**
     * Convierte un monto de una moneda a otra ("SOLES" / "DOLARES") con el tipo de cambio dado
     * (soles por dólar). Redondea a 2 decimales.
     */
    public static BigDecimal convertir(BigDecimal monto, String monedaOrigen, String monedaDestino, BigDecimal tipoCambio) {
        if (monto == null || monedaOrigen.equals(monedaDestino)) {
            return monto;
        }
        if (tipoCambio == null) {
            throw new IllegalArgumentException("Falta el tipo de cambio para convertir de " + monedaOrigen + " a " + monedaDestino + ".");
        }
        return "DOLARES".equals(monedaOrigen)
                ? monto.multiply(tipoCambio).setScale(2, RoundingMode.HALF_UP)      // US$ → S/
                : monto.divide(tipoCambio, 2, RoundingMode.HALF_UP);                  // S/ → US$
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RespuestaSunat(LocalDate fecha, BigDecimal compra, BigDecimal venta) {}
}
