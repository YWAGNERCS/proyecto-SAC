package com.bomerp.config.tipocambio;

import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDate;

/** Tipo de cambio SUNAT para que la SPA lo muestre y lo proponga al vender o cotizar. */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/tipo-cambio")
public class TipoCambioController {

    private final TipoCambioService tipoCambioService;

    public TipoCambioController(TipoCambioService tipoCambioService) {
        this.tipoCambioService = tipoCambioService;
    }

    /**
     * Hoy si no se envía fecha (yyyy-MM-dd). 503 si SUNAT no responde.
     *
     * Cache-Control (S13): SUNAT publica un valor por día, así que el navegador puede reusar la
     * respuesta 10 minutos sin volver a pedirla (al abrir otra venta o proforma). "private": la
     * respuesta lleva token, ningún proxy intermedio debe guardarla. Un 503 no se cachea.
     */
    @GetMapping
    public ResponseEntity<TipoCambioService.TipoCambio> obtener(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        log.info("Tipo de cambio pedido por el navegador (fecha {})", fecha != null ? fecha : "hoy");
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(MAX_AGE).cachePrivate())
                .body(tipoCambioService.obtener(fecha));
    }

    private static final Duration MAX_AGE = Duration.ofMinutes(10);
}
