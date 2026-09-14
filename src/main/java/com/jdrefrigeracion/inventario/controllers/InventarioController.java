package com.jdrefrigeracion.inventario.controllers;
import com.jdrefrigeracion.inventario.services.InventarioService;

import com.jdrefrigeracion.catalogo.models.Producto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/inventario")
public class InventarioController {

    private final InventarioService inventarioService;
    private final com.jdrefrigeracion.inventario.services.InventarioScheduledService inventarioScheduledService;

    public InventarioController(InventarioService inventarioService, com.jdrefrigeracion.inventario.services.InventarioScheduledService inventarioScheduledService) {
        this.inventarioService = inventarioService;
        this.inventarioScheduledService = inventarioScheduledService;
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
}
