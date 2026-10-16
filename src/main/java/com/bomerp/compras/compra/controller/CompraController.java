package com.bomerp.compras.compra.controller;

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

    public CompraController(CompraService compraService, CompraReporteService compraReporteService) {
        this.compraService = compraService;
        this.compraReporteService = compraReporteService;
    }

    /** Compras con filtros opcionales: rango de fechas (yyyy-MM-dd) y proveedor. */
    @GetMapping
    public List<CompraResponseDTO> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long proveedorId) {
        return compraService.buscar(desde, hasta, proveedorId).stream()
                .map(CompraResponseDTO::desdeEntidad)
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
                .map(CompraResponseDTO::desdeEntidad)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<CompraResponseDTO> registrarCompra(@Valid @RequestBody CompraRequestDTO requestDTO) {
        Compra compraGuardada = compraService.registrarCompra(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(CompraResponseDTO.desdeEntidad(compraGuardada));
    }
}
