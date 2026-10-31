package com.bomerp.compras.pago.controller;

import com.bomerp.compras.pago.service.CuentasPorPagarService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Cuentas por pagar a proveedores. Consultar y registrar pagos: cualquiera de los dos roles;
 * anular un pago: ADMIN o el permiso ANULAR_COBROS (regla en SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/admin/cuentas-por-pagar")
public class CuentasPorPagarController {

    private final CuentasPorPagarService service;

    public CuentasPorPagarController(CuentasPorPagarService service) {
        this.service = service;
    }

    /** Compras a crédito con saldo y totales por moneda. */
    @GetMapping
    public CuentasPorPagarService.CuentasDTO pendientes(@RequestParam(required = false) Long proveedorId,
                                                        @RequestParam(defaultValue = "false") boolean soloVencidas) {
        return service.pendientes(proveedorId, soloVencidas);
    }

    @GetMapping("/pagos")
    public List<CuentasPorPagarService.PagoDTO> pagos(
            @RequestParam(required = false) Long proveedorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return service.pagos(proveedorId, desde, hasta);
    }

    @GetMapping("/compras/{compraId}/pagos")
    public List<CuentasPorPagarService.PagoDTO> pagosDeCompra(@PathVariable Long compraId) {
        return service.pagosDeCompra(compraId);
    }

    @PostMapping("/pagos")
    @ResponseStatus(HttpStatus.CREATED)
    public CuentasPorPagarService.PagoDTO registrar(@Valid @RequestBody CuentasPorPagarService.PagoRequest request,
                                                   @AuthenticationPrincipal Jwt jwt) {
        return service.registrar(request, jwt.getClaimAsString("preferred_username"));
    }

    @PostMapping("/pagos/{id}/anular")
    public CuentasPorPagarService.PagoDTO anular(@PathVariable Long id, @Valid @RequestBody CuentasPorPagarService.Anulacion request,
                                                 @AuthenticationPrincipal Jwt jwt) {
        return service.anular(id, request.motivo(), jwt.getClaimAsString("preferred_username"));
    }
}
