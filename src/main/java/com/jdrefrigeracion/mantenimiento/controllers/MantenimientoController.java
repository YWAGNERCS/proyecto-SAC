package com.jdrefrigeracion.mantenimiento.controllers;

import com.jdrefrigeracion.mantenimiento.models.Mantenimiento;
import com.jdrefrigeracion.mantenimiento.repositories.MantenimientoRepository;
import com.jdrefrigeracion.mantenimiento.services.MantenimientoScheduledService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/mantenimientos")
public class MantenimientoController {

    private final MantenimientoRepository mantenimientoRepository;
    private final MantenimientoScheduledService mantenimientoScheduledService;

    public MantenimientoController(MantenimientoRepository mantenimientoRepository, MantenimientoScheduledService mantenimientoScheduledService) {
        this.mantenimientoRepository = mantenimientoRepository;
        this.mantenimientoScheduledService = mantenimientoScheduledService;
    }

    @GetMapping
    public List<Mantenimiento> listarTodos() {
        return mantenimientoRepository.findAll();
    }

    @PostMapping("/{id}/marcar-realizado")
    public Mantenimiento marcarComoRealizado(@PathVariable Long id) {
        Mantenimiento mantenimiento = mantenimientoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Mantenimiento no encontrado: " + id));
        mantenimiento.setEstado(Mantenimiento.EstadoMantenimiento.REALIZADO);
        return mantenimientoRepository.save(mantenimiento);
    }

    /**
     * Endpoint especial de pruebas para disparar manualmente el Cron Job
     * y simular que ha llegado el momento de avisar.
     */
    @PostMapping("/simular-alerta")
    public String simularAlerta() {
        mantenimientoScheduledService.procesarAlertas();
        return "Proceso de alertas ejecutado manualmente. Revisa los logs y la bandeja de entrada de la oficina.";
    }
}
