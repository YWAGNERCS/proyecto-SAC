package com.bomerp.cobranza.cobro.controller;

import com.bomerp.cobranza.cobro.dto.CobroRequestDTO;
import com.bomerp.cobranza.cobro.dto.CobroResponseDTO;
import com.bomerp.cobranza.cobro.dto.CuentaPorCobrarDTO;
import com.bomerp.cobranza.cobro.dto.PagadoDeVentaDTO;
import com.bomerp.cobranza.cobro.service.CobranzaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/cobranza")
public class CobranzaController {

    private final CobranzaService cobranzaService;

    public CobranzaController(CobranzaService cobranzaService) {
        this.cobranzaService = cobranzaService;
    }

    /** Ventas a crédito con saldo pendiente (opcional: de un cliente). */
    @GetMapping("/por-cobrar")
    public List<CuentaPorCobrarDTO> porCobrar(@RequestParam(required = false) Long clienteId) {
        return cobranzaService.porCobrar(clienteId);
    }

    /** Historial de cobros (opcional: de un cliente o de una venta). */
    @GetMapping("/cobros")
    public List<CobroResponseDTO> historial(@RequestParam(required = false) Long clienteId,
                                            @RequestParam(required = false) Long ventaId) {
        return cobranzaService.historial(clienteId, ventaId);
    }

    /** Cuánto pagó el cliente de una venta: se muestra antes de anularla (se le devolverá). */
    @GetMapping("/ventas/{ventaId}/pagado")
    public PagadoDeVentaDTO pagadoDeVenta(@PathVariable Long ventaId) {
        return cobranzaService.pagadoDeVenta(ventaId);
    }

    /** Registra un pago (abono parcial o total a una o varias ventas). */
    @PostMapping("/cobros")
    @ResponseStatus(HttpStatus.CREATED)
    public CobroResponseDTO registrar(@Valid @RequestBody CobroRequestDTO request, @AuthenticationPrincipal Jwt jwt) {
        // Quién registra sale del token ya validado, nunca de un campo del request
        return cobranzaService.registrar(request, jwt.getClaimAsString("preferred_username"));
    }

    /** Anula un cobro mal registrado (solo ADMIN, ver SecurityConfig): deja de contar en el saldo. */
    @PostMapping("/cobros/{id}/anular")
    public CobroResponseDTO anular(@PathVariable Long id, @Valid @RequestBody AnulacionCobroRequest request,
                                   @AuthenticationPrincipal Jwt jwt) {
        return cobranzaService.anular(id, request.motivo(), jwt.getClaimAsString("preferred_username"));
    }

    public record AnulacionCobroRequest(
            @NotBlank(message = "Indique el motivo de la anulación") @Size(max = 300) String motivo) {}
}
