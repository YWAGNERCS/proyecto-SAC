package com.jdrefrigeracion.ventas.controllers;
import com.jdrefrigeracion.ventas.models.Venta;
import com.jdrefrigeracion.ventas.services.VentaService;
import com.jdrefrigeracion.ventas.dtos.VentaResponseDTO;

import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/ventas")
public class VentaController {

    private final VentaService ventaService;

    public VentaController(VentaService ventaService) {
        this.ventaService = ventaService;
    }

    @GetMapping
    public List<VentaResponseDTO> listar() {
        return ventaService.listarTodas().stream().map(VentaResponseDTO::desdeEntidad).toList();
    }

    /** Endpoint estrella: conversión de cotización aceptada en venta */
    @PostMapping("/desde-cotizacion/{cotizacionId}")
    public VentaResponseDTO convertirDesdeCotizacion(
            @PathVariable Long cotizacionId,
            @RequestParam Venta.TipoPago tipoPago,
            @RequestParam(required = false) Integer diasCredito) {
        Venta venta = ventaService.convertirCotizacionEnVenta(cotizacionId, tipoPago, diasCredito);
        return VentaResponseDTO.desdeEntidad(venta);
    }

    @PostMapping("/directa")
    public VentaResponseDTO registrarVentaDirecta(@RequestBody VentaDirectaRequest request) {
        Venta venta = ventaService.registrarVentaDirecta(
                request.clienteId(), request.items(), request.tipoPago(), request.diasCredito());
        return VentaResponseDTO.desdeEntidad(venta);
    }

    @GetMapping("/pagos-atrasados")
    public List<VentaResponseDTO> listarPagosAtrasados() {
        return ventaService.listarVentasConPagoAtrasado().stream()
                .map(VentaResponseDTO::desdeEntidad).toList();
    }

    @PostMapping("/{id}/marcar-pagada")
    public VentaResponseDTO marcarComoPagada(@PathVariable Long id) {
        return VentaResponseDTO.desdeEntidad(ventaService.marcarComoPagada(id));
    }

    public record VentaDirectaRequest(
            @NotNull Long clienteId,
            @NotNull List<VentaService.ItemVentaDTO> items,
            @NotNull Venta.TipoPago tipoPago,
            Integer diasCredito
    ) {}
}
