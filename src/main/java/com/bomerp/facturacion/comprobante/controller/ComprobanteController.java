package com.bomerp.facturacion.comprobante.controller;
import com.bomerp.facturacion.comprobante.entity.Comprobante;
import com.bomerp.facturacion.facturacion.service.FacturacionService;
import com.bomerp.facturacion.notacredito.entity.NotaCredito;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/facturacion")
public class ComprobanteController {

    private final FacturacionService facturacionService;

    public ComprobanteController(FacturacionService facturacionService) {
        this.facturacionService = facturacionService;
    }

    @PostMapping("/emitir/{ventaId}")
    public Comprobante emitir(@PathVariable Long ventaId) {
        return facturacionService.emitirComprobanteParaVenta(ventaId);
    }

    @PostMapping("/{id}/enviar")
    public void enviarFactura(@PathVariable Long id) {
        facturacionService.enviarFacturaCorreo(id);
    }

    /** Notas de crédito emitidas (al anular ventas con comprobante aceptado). */
    @GetMapping("/notas-credito")
    public List<NotaCredito> listarNotasCredito() {
        return facturacionService.listarNotasCredito();
    }
}
