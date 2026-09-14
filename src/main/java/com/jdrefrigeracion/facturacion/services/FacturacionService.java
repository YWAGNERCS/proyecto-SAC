package com.jdrefrigeracion.facturacion.services;
import com.jdrefrigeracion.facturacion.models.Comprobante;
import com.jdrefrigeracion.facturacion.integrations.OseClient;
import com.jdrefrigeracion.facturacion.repositories.ComprobanteRepository;

import com.jdrefrigeracion.clientes.integrations.Cliente;
import com.jdrefrigeracion.ventas.models.Venta;
import com.jdrefrigeracion.ventas.repositories.VentaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FacturacionService {

    private final ComprobanteRepository comprobanteRepository;
    private final VentaRepository ventaRepository;
    private final OseClient oseClient;
    private final com.jdrefrigeracion.notificaciones.services.NotificadorCorreo notificadorCorreo;
    private final GeneradorPdfFacturacionService generadorPdfFacturacionService;

    public FacturacionService(ComprobanteRepository comprobanteRepository, VentaRepository ventaRepository, OseClient oseClient, com.jdrefrigeracion.notificaciones.services.NotificadorCorreo notificadorCorreo, GeneradorPdfFacturacionService generadorPdfFacturacionService) {
        this.comprobanteRepository = comprobanteRepository;
        this.ventaRepository = ventaRepository;
        this.oseClient = oseClient;
        this.notificadorCorreo = notificadorCorreo;
        this.generadorPdfFacturacionService = generadorPdfFacturacionService;
    }

    @Transactional
    public Comprobante emitirComprobanteParaVenta(Long ventaId) {
        Venta venta = ventaRepository.findById(ventaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Venta no encontrada: " + ventaId));

        // Verificamos si ya tiene comprobante
        if (comprobanteRepository.findByVentaId(ventaId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La venta ya tiene un comprobante emitido.");
        }

        Comprobante comprobante = new Comprobante();
        comprobante.setVenta(venta);
        comprobante.setTotal(venta.calcularTotal());

        // Lógica Simple (Patrón O - Open/Closed aplicable a futuro aquí)
        if (venta.getCliente().getTipo() == Cliente.TipoCliente.EMPRESA) {
            comprobante.setTipo(Comprobante.TipoComprobante.FACTURA);
            comprobante.setSerie("F001");
        } else {
            comprobante.setTipo(Comprobante.TipoComprobante.BOLETA);
            comprobante.setSerie("B001");
        }

        // TODO: Simulación de obtener el siguiente correlativo de base de datos
        comprobante.setNumero(String.format("%08d", (int)(Math.random() * 10000)));

        // Guardamos antes de enviar al OSE
        Comprobante comprobanteGuardado = comprobanteRepository.save(comprobante);

        // Actualizamos la venta
        venta.setComprobanteId(comprobanteGuardado.getId());
        ventaRepository.save(venta);

        // 3. Enviamos al OSE (Nubefact)
        OseClient.RespuestaOse respuesta = oseClient.emitir(comprobanteGuardado);

        if (respuesta.exito()) {
            comprobanteGuardado.setEstadoSunat(Comprobante.EstadoSunat.ACEPTADO);
            comprobanteGuardado.setEnlacePdf(respuesta.enlacePdf());
            comprobanteGuardado.setEnlaceXml(respuesta.enlaceXml());
        } else {
            comprobanteGuardado.setEstadoSunat(Comprobante.EstadoSunat.RECHAZADO);
            // Logear respuesta.mensajeError()
        }

        return comprobanteRepository.save(comprobanteGuardado);
    }
    
    public void enviarFacturaCorreo(Long comprobanteId) {
        Comprobante comprobante = comprobanteRepository.findById(comprobanteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comprobante no encontrado: " + comprobanteId));
        
        String correoDestino = comprobante.getVenta().getCliente().getCorreo();
        if (correoDestino == null || correoDestino.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El cliente no tiene correo registrado");
        }

        String tipoDoc = comprobante.getTipo() == Comprobante.TipoComprobante.FACTURA ? "Factura Electrónica" : "Boleta Electrónica";
        String numeroDoc = comprobante.getSerie() + "-" + comprobante.getNumero();
        
        String cuerpoHtml = "<!DOCTYPE html><html><body style='font-family: Arial, sans-serif; background-color: #f4f4f4; padding: 20px;'>" +
                            "<div style='max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 8px; padding: 30px; box-shadow: 0 4px 8px rgba(0,0,0,0.1);'>" +
                            "<h2 style='color: #0078d4;'>Documento Emitido: " + tipoDoc + "</h2>" +
                            "<p>Estimado cliente <strong>" + comprobante.getVenta().getCliente().getNombreORazonSocial() + "</strong>,</p>" +
                            "<p>Le informamos que se ha emitido su comprobante de pago <strong>" + numeroDoc + "</strong> por el monto de <strong>" + comprobante.getVenta().getMoneda() + " " + comprobante.getTotal() + "</strong>.</p>" +
                            "<p>Adjunto a este correo encontrará la representación impresa de su documento (PDF).</p>" +
                            "<hr style='border: none; border-top: 1px solid #eee; margin: 20px 0;'/>" +
                            "<p style='color: #888; font-size: 12px;'>Este documento es una representación impresa de un comprobante de pago electrónico.</p>" +
                            "</div></body></html>";
        
        java.io.File pdfAdjunto = null;
        try {
            pdfAdjunto = generadorPdfFacturacionService.generarFacturaPdf(comprobante);
            notificadorCorreo.enviarCorreoConAdjunto(correoDestino, "Su " + tipoDoc + " " + numeroDoc + " ha sido emitida", cuerpoHtml, pdfAdjunto);
        } catch (Exception e) {
            System.err.println("Error generando PDF de factura: " + e.getMessage());
            // Fallback sin adjunto si falla la generación
            notificadorCorreo.enviarCorreo(correoDestino, "Su " + tipoDoc + " " + numeroDoc + " ha sido emitida", cuerpoHtml);
        } finally {
            if (pdfAdjunto != null && pdfAdjunto.exists()) {
                pdfAdjunto.delete();
            }
        }
    }
}
