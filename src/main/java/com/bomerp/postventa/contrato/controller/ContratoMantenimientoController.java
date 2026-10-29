package com.bomerp.postventa.contrato.controller;

import com.bomerp.postventa.contrato.dto.ContratoDTO;
import com.bomerp.postventa.contrato.entity.ContratoMantenimiento;
import com.bomerp.postventa.contrato.service.ContratoMantenimientoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Contratos de mantenimiento. Crear y cancelar es solo ADMIN (ver SecurityConfig); consultar, ambos roles. */
@RestController
@RequestMapping("/api/v1/admin/contratos")
public class ContratoMantenimientoController {

    private final ContratoMantenimientoService contratoService;

    public ContratoMantenimientoController(ContratoMantenimientoService contratoService) {
        this.contratoService = contratoService;
    }

    @GetMapping
    public List<ContratoDTO.Response> listar(@RequestParam(required = false) Long clienteId,
                                             @RequestParam(required = false) ContratoMantenimiento.Estado estado) {
        return contratoService.listar(clienteId, estado);
    }

    @GetMapping("/{id}")
    public ContratoDTO.Response obtener(@PathVariable Long id) {
        return contratoService.obtener(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContratoDTO.Response crear(@Valid @RequestBody ContratoDTO.Request request, @AuthenticationPrincipal Jwt jwt) {
        return contratoService.crear(request, jwt.getClaimAsString("preferred_username"));
    }

    @PostMapping("/{id}/cancelar")
    public ContratoDTO.Response cancelar(@PathVariable Long id, @Valid @RequestBody ContratoDTO.Cancelacion request) {
        return contratoService.cancelar(id, request.motivo());
    }
}
