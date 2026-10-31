package com.bomerp.reportes.rentabilidad;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Rentabilidad (costos y márgenes): ADMIN o PERM_VER_RENTABILIDAD (SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin/reportes/rentabilidad")
public class RentabilidadController {

    private final RentabilidadService service;

    public RentabilidadController(RentabilidadService service) {
        this.service = service;
    }

    @GetMapping
    public RentabilidadService.Rentabilidad calcular(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return service.calcular(desde, hasta);
    }
}
