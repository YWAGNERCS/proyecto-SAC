package com.bomerp.config.tipocambio;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Tipo de cambio SUNAT para que la SPA lo muestre y lo proponga al vender o cotizar. */
@RestController
@RequestMapping("/api/v1/admin/tipo-cambio")
public class TipoCambioController {

    private final TipoCambioService tipoCambioService;

    public TipoCambioController(TipoCambioService tipoCambioService) {
        this.tipoCambioService = tipoCambioService;
    }

    /** Hoy si no se envía fecha (yyyy-MM-dd). 503 si SUNAT no responde. */
    @GetMapping
    public TipoCambioService.TipoCambio obtener(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return tipoCambioService.obtener(fecha);
    }
}
