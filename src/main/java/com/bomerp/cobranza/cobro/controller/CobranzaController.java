package com.bomerp.cobranza.cobro.controller;

import com.bomerp.cobranza.cobro.dto.CobroRequestDTO;
import com.bomerp.cobranza.cobro.dto.CobroResponseDTO;
import com.bomerp.cobranza.cobro.dto.CuentaPorCobrarDTO;
import com.bomerp.cobranza.cobro.service.CobranzaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

    /** Registra un pago (abono parcial o total a una o varias ventas). */
    @PostMapping("/cobros")
    @ResponseStatus(HttpStatus.CREATED)
    public CobroResponseDTO registrar(@Valid @RequestBody CobroRequestDTO request) {
        return cobranzaService.registrar(request);
    }
}
