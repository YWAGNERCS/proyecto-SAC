package com.bomerp.servicios.orden.controller;

import com.bomerp.servicios.orden.dto.OrdenDTO;
import com.bomerp.servicios.orden.entity.OrdenTrabajo;
import com.bomerp.servicios.orden.service.OrdenTrabajoService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Órdenes de trabajo. Cancelar es solo ADMIN (ver SecurityConfig); el resto, ambos roles. */
@RestController
@RequestMapping("/api/v1/admin/ordenes")
public class OrdenTrabajoController {

    private final OrdenTrabajoService ordenService;

    public OrdenTrabajoController(OrdenTrabajoService ordenService) {
        this.ordenService = ordenService;
    }

    @GetMapping
    public List<OrdenDTO.Response> listar(@RequestParam(required = false) OrdenTrabajo.Estado estado,
                                          @RequestParam(required = false) Long clienteId,
                                          @RequestParam(required = false) String tecnico,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ordenService.listar(estado, clienteId, tecnico, desde, hasta);
    }

    /** Técnicos que ya tienen órdenes, para sugerirlos al programar. */
    /** Agenda semanal de técnicos (fechas yyyy-MM-dd, hasta 6 semanas). */
    @GetMapping("/agenda")
    public List<OrdenDTO.Response> agenda(
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate desde,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate hasta) {
        return ordenService.agenda(desde, hasta);
    }

    @GetMapping("/tecnicos")
    public List<String> tecnicos() {
        return ordenService.tecnicos();
    }

    @GetMapping("/{id}")
    public OrdenDTO.Response obtener(@PathVariable Long id) {
        return ordenService.obtener(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrdenDTO.Response crear(@Valid @RequestBody OrdenDTO.Request request, @AuthenticationPrincipal Jwt jwt) {
        return ordenService.crear(request, jwt.getClaimAsString("preferred_username"));
    }

    @PutMapping("/{id}")
    public OrdenDTO.Response actualizar(@PathVariable Long id, @Valid @RequestBody OrdenDTO.Request request) {
        return ordenService.actualizar(id, request);
    }

    @PostMapping("/{id}/cerrar")
    public OrdenDTO.Response cerrar(@PathVariable Long id, @Valid @RequestBody OrdenDTO.Cierre request,
                                    @AuthenticationPrincipal Jwt jwt) {
        return ordenService.cerrar(id, request, jwt.getClaimAsString("preferred_username"));
    }

    @PostMapping("/{id}/cancelar")
    public OrdenDTO.Response cancelar(@PathVariable Long id, @Valid @RequestBody OrdenDTO.Cancelacion request) {
        return ordenService.cancelar(id, request.motivo());
    }

    /** Factura la mano de obra de una orden realizada (crea una venta directa vinculada a la orden). */
    @PostMapping("/{id}/facturar")
    public OrdenDTO.Response facturar(@PathVariable Long id, @Valid @RequestBody OrdenDTO.Facturacion request,
                                      @AuthenticationPrincipal Jwt jwt) {
        // El vendedor sale del token ya validado (sub), nunca de un campo del request
        return ordenService.facturar(id, request, jwt.getSubject(), jwt.getClaimAsString("preferred_username"));
    }
}
