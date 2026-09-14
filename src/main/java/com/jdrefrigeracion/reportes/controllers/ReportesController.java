package com.jdrefrigeracion.reportes.controllers;

import com.jdrefrigeracion.reportes.services.ReportesService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/reportes")
public class ReportesController {

    private final ReportesService reportesService;

    public ReportesController(ReportesService reportesService) {
        this.reportesService = reportesService;
    }

    /**
     * RF: "Ventas por periodo". Sin parámetros, devuelve el mes actual.
     * Ejemplo: /api/admin/reportes/ventas-resumen?desde=2026-09-01&hasta=2026-09-30
     */
    @GetMapping("/ventas-resumen")
    public ResponseEntity<Map<String, Object>> getResumenVentas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(reportesService.obtenerResumenVentas(desde, hasta));
    }

    /** RF: "Pagos atrasados" — ahora separado de "pendientes no vencidos" */
    @GetMapping("/cobranza")
    public ResponseEntity<Map<String, Object>> getReporteCobranza() {
        return ResponseEntity.ok(reportesService.obtenerReporteCobranza());
    }

    /** RF: "Cotizaciones pendientes" */
    @GetMapping("/cotizaciones-pendientes")
    public ResponseEntity<Map<String, Object>> getCotizacionesPendientes() {
        return ResponseEntity.ok(reportesService.obtenerReporteCotizacionesPendientes());
    }
}
