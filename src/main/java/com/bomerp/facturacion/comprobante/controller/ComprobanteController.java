package com.bomerp.facturacion.comprobante.controller;
import com.bomerp.facturacion.comprobante.entity.Comprobante;
import com.bomerp.facturacion.facturacion.service.FacturacionService;
import com.bomerp.facturacion.notacredito.entity.NotaCredito;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    /** Descarga el PDF de la factura o boleta de una venta (404 si no tiene comprobante). */
    @GetMapping("/ventas/{ventaId}/pdf")
    public ResponseEntity<byte[]> descargarPdf(@PathVariable Long ventaId) {
        FacturacionService.PdfComprobante pdf = facturacionService.pdfDeVenta(ventaId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(pdf.nombreArchivo()).build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf.contenido());
    }

    /** Notas de crédito emitidas (al anular ventas con comprobante aceptado). */
    @GetMapping("/notas-credito")
    public List<NotaCredito> listarNotasCredito() {
        return facturacionService.listarNotasCredito();
    }
}
