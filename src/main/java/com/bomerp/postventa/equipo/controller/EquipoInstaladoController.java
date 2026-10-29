package com.bomerp.postventa.equipo.controller;

import com.bomerp.postventa.equipo.dto.EquipoDTO;
import com.bomerp.postventa.equipo.entity.EquipoInstalado;
import com.bomerp.postventa.equipo.service.EquipoInstaladoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Equipos instalados en los locales de los clientes. Retirar uno es solo ADMIN (ver SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin/equipos")
public class EquipoInstaladoController {

    private final EquipoInstaladoService equipoService;

    public EquipoInstaladoController(EquipoInstaladoService equipoService) {
        this.equipoService = equipoService;
    }

    /** Parque instalado; filtros opcionales por cliente, sede, estado y si está en garantía. */
    @GetMapping
    public List<EquipoDTO.Response> listar(@RequestParam(required = false) Long clienteId,
                                           @RequestParam(required = false) Long sedeId,
                                           @RequestParam(required = false) EquipoInstalado.Estado estado,
                                           @RequestParam(required = false) Boolean enGarantia) {
        return equipoService.listar(clienteId, sedeId, estado, enGarantia);
    }

    @GetMapping("/{id}")
    public EquipoDTO.Response obtener(@PathVariable Long id) {
        return equipoService.obtener(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EquipoDTO.Response registrar(@Valid @RequestBody EquipoDTO.Registro request) {
        return equipoService.registrar(request);
    }

    @PutMapping("/{id}")
    public EquipoDTO.Response actualizar(@PathVariable Long id, @Valid @RequestBody EquipoDTO.Edicion request) {
        return equipoService.actualizar(id, request);
    }

    @PostMapping("/{id}/retirar")
    public EquipoDTO.Response retirar(@PathVariable Long id, @Valid @RequestBody EquipoDTO.Retiro request) {
        return equipoService.retirar(id, request.motivo());
    }
}
