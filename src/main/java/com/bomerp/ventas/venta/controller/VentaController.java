package com.bomerp.ventas.venta.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import com.bomerp.ventas.venta.entity.Venta;
import com.bomerp.ventas.venta.event.RutinaMantenimiento;
import com.bomerp.ventas.venta.service.VentaService;
import com.bomerp.ventas.venta.dto.VentaResponseDTO;
import com.bomerp.ventas.reporte.service.VentaReporteService;
import com.bomerp.config.exportacion.ExcelBuilder;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    /** Reporte de ventas por páginas, con los totales del filtro completo (pantalla de Ventas). */
    @GetMapping("/pagina")
    public VentaService.ReporteVentas pagina(
            @RequestParam(required = false) Venta.EstadoVenta estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false) Integer pagina,
            @RequestParam(required = false) Integer tamano,
            @RequestParam(required = false) String orden) {
        return ventaService.reportePagina(estado, desde, hasta, clienteId, pagina, tamano, orden);
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

    /**
     * Transición REGISTRADA -> ANULADA; restaura el stock. 409 si ya estaba anulada. Motivo obligatorio y quién
     * anuló (del token), como en Cobranza (auditoría M4).
     */
    @PatchMapping("/{id}/anular")
    public VentaResponseDTO anular(@PathVariable Long id, @Valid @RequestBody AnulacionVentaRequest request,
                                   @AuthenticationPrincipal Jwt jwt) {
        return ventaService.anular(id, request.motivo(), jwt.getClaimAsString("preferred_username"));
    }

    public record AnulacionVentaRequest(
            @NotBlank(message = "Indique el motivo de la anulación")
            @Size(min = 5, max = 300, message = "El motivo debe tener entre 5 y 300 caracteres") String motivo) {}

    /** Endpoint estrella: conversión de proforma aceptada en venta */
    @PostMapping("/desde-proforma/{proformaId}")
    public VentaResponseDTO convertirDesdeProforma(
            @PathVariable Long proformaId,
            @RequestParam Venta.TipoPago tipoPago,
            @RequestParam(required = false) Integer diasCredito,
            @RequestBody(required = false) ConversionRequest cuerpo,
            @AuthenticationPrincipal Jwt jwt) {
        // El vendedor sale del token ya validado (sub), nunca de un campo del request
        return ventaService.convertirProformaEnVenta(proformaId, tipoPago, diasCredito,
                jwt.getSubject(), jwt.getClaimAsString("preferred_username"),
                cuerpo != null ? cuerpo.rutinaMantenimiento() : null,
                cuerpo != null ? cuerpo.fechaVenta() : null);
    }

    /**
     * Cuerpo opcional de la conversión: la rutina de mantenimiento elegida (null = sin mantenimientos) y la fecha
     * real de la venta (null = hoy; con otra fecha, hasta 30 días atrás y con permiso: auditoría M3).
     */
    public record ConversionRequest(RutinaMantenimiento rutinaMantenimiento, LocalDate fechaVenta) {}

    /**
     * Venta directa. @Valid aplica las restricciones del cuerpo (auditoría B9): antes no se aplicaban y una
     * llamada sin tipoPago se registraba como contado sin avisar; ahora responde 400 con el campo que falta.
     */
    @PostMapping("/directa")
    public VentaResponseDTO registrarVentaDirecta(@Valid @RequestBody VentaDirectaRequest request,
                                                  @AuthenticationPrincipal Jwt jwt) {
        return ventaService.registrarVentaDirecta(
                request.clienteId(), request.items(), request.tipoPago(), request.diasCredito(),
                request.moneda(), request.tipoCambio(),
                jwt.getSubject(), jwt.getClaimAsString("preferred_username"), request.rutinaMantenimiento(),
                request.fechaVenta());
    }

    @GetMapping("/pagos-atrasados")
    public List<VentaResponseDTO> listarPagosAtrasados() {
        return ventaService.listarVentasConPagoAtrasado();
    }

    public record VentaDirectaRequest(
            @NotNull(message = "Indique el cliente.") Long clienteId,
            @NotEmpty(message = "La venta necesita al menos un producto.") @Valid List<VentaService.ItemVentaDTO> items,
            @NotNull(message = "Indique la forma de pago (CONTADO o CREDITO).") Venta.TipoPago tipoPago,
            Integer diasCredito,
            Venta.Moneda moneda,                     // opcional: moneda en la que se cobra
            java.math.BigDecimal tipoCambio,         // opcional: soles por dólar (null = SUNAT del día)
            RutinaMantenimiento rutinaMantenimiento, // opcional: botón "Implementar rutinas de mantenimiento"
            LocalDate fechaVenta                     // opcional: fecha real (null = hoy; hasta 30 días atrás, con permiso)
    ) {}
}
