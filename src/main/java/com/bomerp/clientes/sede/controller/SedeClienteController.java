package com.bomerp.clientes.sede.controller;

import com.bomerp.clientes.sede.dto.SedeDTO;
import com.bomerp.clientes.sede.service.SedeClienteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Sedes de un cliente. Eliminar es solo ADMIN (regla DELETE /clientes/** de SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin/clientes/{clienteId}/sedes")
public class SedeClienteController {

    private final SedeClienteService sedeService;

    public SedeClienteController(SedeClienteService sedeService) {
        this.sedeService = sedeService;
    }

    @GetMapping
    public List<SedeDTO.Response> listar(@PathVariable Long clienteId) {
        return sedeService.listar(clienteId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SedeDTO.Response crear(@PathVariable Long clienteId, @Valid @RequestBody SedeDTO.Request request) {
        return sedeService.crear(clienteId, request);
    }

    @PutMapping("/{sedeId}")
    public SedeDTO.Response actualizar(@PathVariable Long clienteId, @PathVariable Long sedeId,
                                       @Valid @RequestBody SedeDTO.Request request) {
        return sedeService.actualizar(clienteId, sedeId, request);
    }

    @DeleteMapping("/{sedeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long clienteId, @PathVariable Long sedeId) {
        sedeService.eliminar(clienteId, sedeId);
    }
}
