package com.bomerp.inventario.inventario.controller;
import com.bomerp.inventario.inventario.service.InventarioService;

import com.bomerp.catalogo.producto.entity.Producto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/inventario")
public class InventarioController {

    private final InventarioService inventarioService;
    private final com.bomerp.inventario.inventario.service.InventarioScheduledService inventarioScheduledService;

    public InventarioController(InventarioService inventarioService, com.bomerp.inventario.inventario.service.InventarioScheduledService inventarioScheduledService) {
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
