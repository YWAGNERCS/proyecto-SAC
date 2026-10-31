package com.bomerp.compras.compra.controller;

import com.bomerp.compras.pago.service.CuentasPorPagarService;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import com.bomerp.compras.reporte.service.CompraReporteService;
import com.bomerp.config.exportacion.ExcelBuilder;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import com.bomerp.compras.compra.service.CompraService;
import com.bomerp.compras.compra.dto.CompraRequestDTO;
import com.bomerp.compras.compra.dto.CompraResponseDTO;
import com.bomerp.compras.compra.entity.Compra;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/compras")
public class CompraController {

    private final CompraService compraService;
    private final CompraReporteService compraReporteService;
    private final CuentasPorPagarService cuentasPorPagarService;

    public CompraController(CompraService compraService, CompraReporteService compraReporteService,
                            CuentasPorPagarService cuentasPorPagarService) {
        this.compraService = compraService;
        this.compraReporteService = compraReporteService;
        this.cuentasPorPagarService = cuentasPorPagarService;
    }

    /** Compras con filtros opcionales: rango de fechas (yyyy-MM-dd) y proveedor. */
    @GetMapping
    public List<CompraResponseDTO> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long proveedorId) {
        List<Compra> compras = compraService.buscar(desde, hasta, proveedorId);
        Map<Long, BigDecimal> pagado = cuentasPorPagarService.pagadoPorCompra(compras);
        return compras.stream()
                .map(c -> CompraResponseDTO.desdeEntidad(c, c.getTipoPago() == Compra.TipoPago.CREDITO
                        ? pagado.getOrDefault(c.getId(), BigDecimal.ZERO) : c.getTotal()))
                .toList();
    }

    /** Mismo listado en Excel (.xlsx): hoja "Compras" y hoja "Detalle". */
    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportarExcel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long proveedorId) {
        byte[] excel = compraReporteService.exportarExcel(desde, hasta, proveedorId);
        return ExcelBuilder.descargar(excel, "compras_" + LocalDate.now() + ".xlsx");
    }

    /** Historial de un proveedor: sus compras y montos por moneda. */
    @GetMapping("/historial-proveedor/{proveedorId}")
    public CompraReporteService.HistorialProveedor historialProveedor(@PathVariable Long proveedorId) {
        return compraReporteService.historialProveedor(proveedorId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CompraResponseDTO> buscarPorId(@PathVariable Long id) {
        return compraService.buscarPorId(id)
                .map(c -> CompraResponseDTO.desdeEntidad(c, c.getTipoPago() == Compra.TipoPago.CREDITO
                        ? cuentasPorPagarService.pagadoPorCompra(List.of(c)).getOrDefault(c.getId(), BigDecimal.ZERO)
                        : c.getTotal()))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<CompraResponseDTO> registrarCompra(@Valid @RequestBody CompraRequestDTO requestDTO,
                                                             @AuthenticationPrincipal Jwt jwt) {
        Compra compraGuardada = compraService.registrarCompra(requestDTO,
                jwt != null ? jwt.getClaimAsString("preferred_username") : null);
        // Recién creada: a crédito todavía no tiene pagos
        return ResponseEntity.status(HttpStatus.CREATED).body(CompraResponseDTO.desdeEntidad(compraGuardada,
                compraGuardada.getTipoPago() == Compra.TipoPago.CREDITO ? BigDecimal.ZERO : compraGuardada.getTotal()));
    }
}
