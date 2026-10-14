package com.bomerp.ventas.venta.controller;
import com.bomerp.ventas.venta.entity.Venta;
import com.bomerp.ventas.venta.service.VentaService;
import com.bomerp.ventas.venta.dto.VentaResponseDTO;

import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/ventas")
public class VentaController {

    private final VentaService ventaService;

    public VentaController(VentaService ventaService) {
        this.ventaService = ventaService;
    }

    /** Reporte de ventas con su detalle. Filtros opcionales: estado y rango de fechas (yyyy-MM-dd). */
    @GetMapping
    public List<VentaResponseDTO> listar(
            @RequestParam(required = false) Venta.EstadoVenta estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ventaService.buscar(estado, desde, hasta).stream().map(VentaResponseDTO::desdeEntidad).toList();
    }

    /** Transición REGISTRADA -> ANULADA; restaura el stock. 409 si ya estaba anulada. */
    @PatchMapping("/{id}/anular")
    public VentaResponseDTO anular(@PathVariable Long id) {
        return VentaResponseDTO.desdeEntidad(ventaService.anular(id));
    }

    /** Endpoint estrella: conversión de proforma aceptada en venta */
    @PostMapping("/desde-proforma/{proformaId}")
    public VentaResponseDTO convertirDesdeProforma(
            @PathVariable Long proformaId,
            @RequestParam Venta.TipoPago tipoPago,
            @RequestParam(required = false) Integer diasCredito) {
        Venta venta = ventaService.convertirProformaEnVenta(proformaId, tipoPago, diasCredito);
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
