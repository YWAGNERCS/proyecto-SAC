package com.bomerp.ventas.venta.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import com.bomerp.ventas.venta.entity.Venta;
import com.bomerp.ventas.venta.service.VentaService;
import com.bomerp.ventas.venta.dto.VentaResponseDTO;
import com.bomerp.ventas.reporte.service.VentaReporteService;
import com.bomerp.config.exportacion.ExcelBuilder;

import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/ventas")
public class VentaController {

    private final VentaService ventaService;
    private final VentaReporteService ventaReporteService;

    public VentaController(VentaService ventaService, VentaReporteService ventaReporteService) {
        this.ventaService = ventaService;
        this.ventaReporteService = ventaReporteService;
    }

    /** Reporte de ventas con su detalle. Filtros opcionales: estado y rango de fechas (yyyy-MM-dd). */
    @GetMapping
    public List<VentaResponseDTO> listar(
            @RequestParam(required = false) Venta.EstadoVenta estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long clienteId) {
        return ventaService.listar(estado, desde, hasta, clienteId);
    }

    /** Mismo reporte en Excel (.xlsx): hoja "Ventas" y hoja "Detalle". Acepta los mismos filtros. */
    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportarExcel(
            @RequestParam(required = false) Venta.EstadoVenta estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long clienteId) {
        byte[] excel = ventaReporteService.exportarExcel(estado, desde, hasta, clienteId);
        return ExcelBuilder.descargar(excel, "ventas_" + LocalDate.now() + ".xlsx");
    }

    /** Historial de un cliente: sus ventas, totales por moneda y saldo pendiente de pago. */
    @GetMapping("/historial-cliente/{clienteId}")
    public VentaReporteService.HistorialCliente historialCliente(@PathVariable Long clienteId) {
        return ventaReporteService.historialCliente(clienteId);
    }

    /** Transición REGISTRADA -> ANULADA; restaura el stock. 409 si ya estaba anulada. */
    @PatchMapping("/{id}/anular")
    public VentaResponseDTO anular(@PathVariable Long id) {
        return ventaService.anular(id);
    }

    /** Endpoint estrella: conversión de proforma aceptada en venta */
    @PostMapping("/desde-proforma/{proformaId}")
    public VentaResponseDTO convertirDesdeProforma(
            @PathVariable Long proformaId,
            @RequestParam Venta.TipoPago tipoPago,
            @RequestParam(required = false) Integer diasCredito,
            @AuthenticationPrincipal Jwt jwt) {
        // El vendedor sale del token ya validado (sub), nunca de un campo del request
        return ventaService.convertirProformaEnVenta(proformaId, tipoPago, diasCredito,
                jwt.getSubject(), jwt.getClaimAsString("preferred_username"));
    }

    @PostMapping("/directa")
    public VentaResponseDTO registrarVentaDirecta(@RequestBody VentaDirectaRequest request,
                                                  @AuthenticationPrincipal Jwt jwt) {
        return ventaService.registrarVentaDirecta(
                request.clienteId(), request.items(), request.tipoPago(), request.diasCredito(),
                request.moneda(), request.tipoCambio(),
                jwt.getSubject(), jwt.getClaimAsString("preferred_username"));
    }

    @GetMapping("/pagos-atrasados")
    public List<VentaResponseDTO> listarPagosAtrasados() {
        return ventaService.listarVentasConPagoAtrasado();
    }

    public record VentaDirectaRequest(
            @NotNull Long clienteId,
            @NotNull List<VentaService.ItemVentaDTO> items,
            @NotNull Venta.TipoPago tipoPago,
            Integer diasCredito,
            Venta.Moneda moneda,                     // opcional: moneda en la que se cobra
            java.math.BigDecimal tipoCambio          // opcional: soles por dólar (null = SUNAT del día)
    ) {}
}
