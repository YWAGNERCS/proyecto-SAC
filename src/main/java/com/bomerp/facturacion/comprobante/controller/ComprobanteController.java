package com.bomerp.facturacion.comprobante.controller;
import com.bomerp.facturacion.comprobante.dto.ComprobanteResponseDTO;
import com.bomerp.facturacion.facturacion.service.FacturacionService;
import com.bomerp.facturacion.facturacion.service.EnvioSunatService;
import com.bomerp.facturacion.comprobante.dto.EstadoSunatVentaDTO;
import com.bomerp.facturacion.notacredito.dto.NotaCreditoResponseDTO;

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
    private final EnvioSunatService envioSunat;

    public ComprobanteController(FacturacionService facturacionService, EnvioSunatService envioSunat) {
        this.facturacionService = facturacionService;
        this.envioSunat = envioSunat;
    }

    /** Estado ante SUNAT de los comprobantes (y notas de crédito) de varias ventas: ?ventaIds=1,2,3 (máx. 200). */
    @GetMapping("/estados")
    public List<EstadoSunatVentaDTO> estados(@RequestParam List<Long> ventaIds) {
        return facturacionService.estadosDeVentas(ventaIds);
    }

    /** Vuelve a enviar a SUNAT un comprobante rechazado (ya corregido) o pendiente. 409 si ya fue aceptado. */
    @PostMapping("/{id}/reenviar")
    public ResponseEntity<Void> reenviar(@PathVariable Long id) {
        envioSunat.reenviarComprobante(id);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/notas-credito/{id}/reenviar")
    public ResponseEntity<Void> reenviarNota(@PathVariable Long id) {
        envioSunat.reenviarNota(id);
        return ResponseEntity.accepted().build();
    }

    /** XML firmado tal como se envió a SUNAT, o su CDR (constancia de recepción, ZIP). */
    @GetMapping("/{id}/{archivo:xml|cdr}")
    public ResponseEntity<byte[]> archivo(@PathVariable Long id, @PathVariable String archivo) {
        return descargar(facturacionService.archivoSunat(false, id, "cdr".equals(archivo)));
    }

    @GetMapping("/notas-credito/{id}/{archivo:xml|cdr}")
    public ResponseEntity<byte[]> archivoNota(@PathVariable Long id, @PathVariable String archivo) {
        return descargar(facturacionService.archivoSunat(true, id, "cdr".equals(archivo)));
    }

    private static ResponseEntity<byte[]> descargar(FacturacionService.ArchivoSunat a) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(a.nombre()).build().toString())
                .contentType(a.esZip() ? MediaType.parseMediaType("application/zip") : MediaType.APPLICATION_XML)
                .body(a.contenido());
    }

    @PostMapping("/emitir/{ventaId}")
    public ComprobanteResponseDTO emitir(@PathVariable Long ventaId) {
        return facturacionService.emitir(ventaId);
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
    public List<NotaCreditoResponseDTO> listarNotasCredito() {
        return facturacionService.listarNotasCredito();
    }
}
