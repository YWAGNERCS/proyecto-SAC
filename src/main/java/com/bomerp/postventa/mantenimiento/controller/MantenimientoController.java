package com.bomerp.postventa.mantenimiento.controller;

import com.bomerp.postventa.mantenimiento.dto.MantenimientoResponseDTO;
import com.bomerp.postventa.mantenimiento.service.MantenimientoScheduledService;
import com.bomerp.postventa.mantenimiento.service.MantenimientoService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/mantenimientos")
public class MantenimientoController {

    private final MantenimientoService mantenimientoService;
    private final MantenimientoScheduledService mantenimientoScheduledService;

    public MantenimientoController(MantenimientoService mantenimientoService,
                                   MantenimientoScheduledService mantenimientoScheduledService) {
        this.mantenimientoService = mantenimientoService;
        this.mantenimientoScheduledService = mantenimientoScheduledService;
    }

    /** Agenda de Postventa: vista = VENCIDOS | PROXIMOS (por defecto, 30 días) | REALIZADOS | TODOS. */
    @GetMapping
    public List<MantenimientoResponseDTO> listar(@RequestParam(defaultValue = "PROXIMOS") String vista,
                                                 @RequestParam(defaultValue = "30") int dias,
                                                 @RequestParam(required = false) Long clienteId) {
        return mantenimientoService.agenda(vista, dias, clienteId);
    }

    /** Contadores para la cabecera: vencidos, próximos 7 y 30 días. */
    @GetMapping("/resumen")
    public Map<String, Integer> resumen() {
        return mantenimientoService.resumen();
    }

    public record RealizadoRequest(LocalDate fecha, String notas) {}

    public record ReprogramarRequest(LocalDate fecha, String notas) {}

    @PatchMapping("/{id}/realizado")
    public MantenimientoResponseDTO marcarRealizado(@PathVariable Long id,
                                                    @RequestBody(required = false) RealizadoRequest request) {
        return mantenimientoService.marcarRealizado(id,
                request != null ? request.fecha() : null, request != null ? request.notas() : null);
    }

    @PatchMapping("/{id}/reprogramar")
    public MantenimientoResponseDTO reprogramar(@PathVariable Long id, @RequestBody ReprogramarRequest request) {
        return mantenimientoService.reprogramar(id, request.fecha(), request.notas());
    }

    /** Compatibilidad con la ruta anterior: marca la visita como realizada hoy. */
    @PostMapping("/{id}/marcar-realizado")
    public MantenimientoResponseDTO marcarComoRealizado(@PathVariable Long id) {
        return mantenimientoService.marcarRealizado(id, null, null);
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
