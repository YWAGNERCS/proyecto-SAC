package com.bomerp.inventario.inventario.controller;
import com.bomerp.inventario.kardex.service.KardexService;
import com.bomerp.config.exportacion.ExcelBuilder;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import java.time.LocalDate;
import com.bomerp.inventario.inventario.service.InventarioService;

import com.bomerp.catalogo.producto.entity.Producto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/inventario")
public class InventarioController {

    private final InventarioService inventarioService;
    private final com.bomerp.inventario.inventario.service.InventarioScheduledService inventarioScheduledService;

    private final KardexService kardexService;

    public InventarioController(InventarioService inventarioService, com.bomerp.inventario.inventario.service.InventarioScheduledService inventarioScheduledService,
                                KardexService kardexService) {
        this.inventarioService = inventarioService;
        this.inventarioScheduledService = inventarioScheduledService;
        this.kardexService = kardexService;
    }

    @GetMapping("/alertas")
    public List<Producto> obtenerAlertas() {
        return inventarioService.obtenerProductosEnAlerta();
    }

    @org.springframework.web.bind.annotation.PostMapping("/simular-reporte")
    public org.springframework.http.ResponseEntity<String> simularReporte() {
        inventarioScheduledService.procesarReporte();
        return org.springframework.http.ResponseEntity.ok("Reporte de stock ejecutado manualmente. Revisa los logs y el correo de la oficina.");
    }

    /** Kardex de un producto: entradas, salidas y devoluciones con el saldo de stock. Fechas opcionales (yyyy-MM-dd). */
    @GetMapping("/kardex/{productoId}")
    public KardexService.Kardex kardex(
            @PathVariable Long productoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return kardexService.kardex(productoId, desde, hasta);
    }

    @GetMapping("/kardex/{productoId}/exportar")
    public org.springframework.http.ResponseEntity<byte[]> exportarKardex(
            @PathVariable Long productoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        byte[] excel = kardexService.exportarExcel(productoId, desde, hasta);
        return ExcelBuilder.descargar(excel, "kardex_producto_" + productoId + "_" + LocalDate.now() + ".xlsx");
    }
}
