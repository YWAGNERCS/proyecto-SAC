package com.bomerp.cobranza.devolucion.controller;

import com.bomerp.cobranza.devolucion.dto.DevolucionRequestDTO;
import com.bomerp.cobranza.devolucion.dto.DevolucionResponseDTO;
import com.bomerp.cobranza.devolucion.entity.Devolucion;
import com.bomerp.cobranza.devolucion.service.DevolucionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/cobranza/devoluciones")
public class DevolucionController {

    private final DevolucionService devolucionService;

    public DevolucionController(DevolucionService devolucionService) {
        this.devolucionService = devolucionService;
    }

    /** Devoluciones por ventas anuladas (opcional: por estado o de un cliente). */
    @GetMapping
    public List<DevolucionResponseDTO> listar(@RequestParam(required = false) Devolucion.Estado estado,
                                              @RequestParam(required = false) Long clienteId) {
        return devolucionService.listar(estado, clienteId);
    }

    /** Registra que el dinero ya se devolvió (solo ADMIN, ver SecurityConfig). */
    @PostMapping("/{id}/registrar")
    public DevolucionResponseDTO registrar(@PathVariable Long id, @Valid @RequestBody DevolucionRequestDTO request) {
        return devolucionService.registrar(id, request);
    }
}
