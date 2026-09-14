package com.jdrefrigeracion.facturacion.controllers;
import com.jdrefrigeracion.facturacion.models.Comprobante;
import com.jdrefrigeracion.facturacion.services.FacturacionService;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/facturacion")
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
}
