package com.jdrefrigeracion.cotizaciones.controllers;
import com.jdrefrigeracion.cotizaciones.models.Cotizacion;
import com.jdrefrigeracion.cotizaciones.models.CotizacionRequest;
import com.jdrefrigeracion.cotizaciones.services.CotizacionService;
import com.jdrefrigeracion.cotizaciones.dtos.CotizacionResponseDTO;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/cotizaciones")
public class CotizacionController {

    private final CotizacionService cotizacionService;

    public CotizacionController(CotizacionService cotizacionService) {
        this.cotizacionService = cotizacionService;
    }

    @GetMapping
    public List<CotizacionResponseDTO> listar(
            @RequestParam(required = false) Cotizacion.EstadoCotizacion estado) {
        List<Cotizacion> cotizaciones = estado != null
                ? cotizacionService.listarPorEstado(estado)
                : cotizacionService.listarTodas();
        return cotizaciones.stream().map(CotizacionResponseDTO::desdeEntidad).toList();
    }

    @PostMapping
    public CotizacionResponseDTO crear(@Valid @RequestBody CotizacionRequest.CrearCotizacionDTO request) {
        Cotizacion creada = cotizacionService.crear(request);
        return CotizacionResponseDTO.desdeEntidad(creada);
    }

    @PostMapping("/{id}/enviar")
    public void enviar(@PathVariable Long id,
                        @RequestParam(defaultValue = "true") boolean whatsapp,
                        @RequestParam(defaultValue = "true") boolean correo) {
        cotizacionService.enviar(id, whatsapp, correo);
    }

    @PostMapping("/{id}/aceptar")
    public CotizacionResponseDTO aceptar(@PathVariable Long id) {
        return CotizacionResponseDTO.desdeEntidad(cotizacionService.aceptar(id));
    }

    @PostMapping("/{id}/rechazar")
    public CotizacionResponseDTO rechazar(@PathVariable Long id) {
        return CotizacionResponseDTO.desdeEntidad(cotizacionService.rechazar(id));
    }
}
