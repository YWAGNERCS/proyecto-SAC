package com.bomerp.inventario.ajuste.controller;

import com.bomerp.inventario.ajuste.entity.AjusteInventario;
import com.bomerp.inventario.ajuste.service.AjusteInventarioService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Ajustes de inventario. Consultar: cualquier usuario; registrar: ADMIN o PERM_AJUSTAR_INVENTARIO (SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin/inventario/ajustes")
public class AjusteInventarioController {

    private final AjusteInventarioService service;

    public AjusteInventarioController(AjusteInventarioService service) {
        this.service = service;
    }

    @GetMapping
    public List<AjusteInventarioService.AjusteDTO> buscar(
            @RequestParam(required = false) Long productoId,
            @RequestParam(required = false) AjusteInventario.Motivo motivo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return service.buscar(productoId, motivo, desde, hasta);
    }

    @GetMapping("/motivos")
    public List<AjusteInventarioService.MotivoDTO> motivos() {
        return service.motivos();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AjusteInventarioService.AjusteDTO registrar(@Valid @RequestBody AjusteInventarioService.AjusteRequest request,
                                                       @AuthenticationPrincipal Jwt jwt) {
        return service.registrar(request, jwt.getClaimAsString("preferred_username"));
    }
}
