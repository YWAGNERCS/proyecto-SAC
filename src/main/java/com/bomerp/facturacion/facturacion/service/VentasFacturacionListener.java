package com.bomerp.facturacion.facturacion.service;

import com.bomerp.facturacion.comprobante.entity.Comprobante;
import com.bomerp.ventas.venta.event.VentaAnuladaEvent;
import com.bomerp.ventas.venta.event.VentaRegistradaEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Facturación reacciona a lo que pasa en Ventas (ADR-002: Ventas publica, Facturación escucha).
 * Corre dentro de la transacción de la venta.
 */
@Component
@Slf4j
public class VentasFacturacionListener {

    private final FacturacionService facturacionService;

    public VentasFacturacionListener(FacturacionService facturacionService) {
        this.facturacionService = facturacionService;
    }

    /** Registra el comprobante (PENDIENTE): se envía a SUNAT al confirmarse la venta. Un error no aborta la venta. */
    @EventListener
    @Order(2)
    public void alRegistrarVenta(VentaRegistradaEvent evento) {
        try {
            Comprobante comp = facturacionService.emitirComprobanteParaVenta(evento.ventaId());
            log.info("Comprobante {}-{} de la Venta {} registrado; se envía a SUNAT en segundo plano",
                    comp.getSerie(), comp.getNumero(), evento.ventaId());
        } catch (Exception e) {
            log.error("Error al emitir el comprobante de la Venta {}: {}", evento.ventaId(), e.getMessage());
        }
    }

    /** Comprobante aceptado por SUNAT: recién ahora se envía por correo al cliente (si tiene correo). */
    @EventListener
    public void alAceptarComprobante(EnvioSunatService.ComprobanteAceptadoEvent evento) {
        try {
            facturacionService.enviarFacturaCorreoSiTieneCorreo(evento.comprobanteId());
        } catch (Exception e) {
            log.warn("Comprobante {} aceptado, pero no se pudo enviar por correo: {}", evento.comprobanteId(), e.getMessage());
        }
    }

    /** Venta anulada: nota de crédito que anula su comprobante aceptado. */
    @EventListener
    @Order(2)
    public void alAnularVenta(VentaAnuladaEvent evento) {
        String nota = facturacionService
                .emitirNotaCreditoPorAnulacion(evento.ventaId(), "Anulación de la operación (venta #" + evento.ventaId() + ")")
                .map(nc -> nc.getSerie() + "-" + nc.getNumero())
                .orElse("no requerida");
        log.info("Venta {} anulada: nota de crédito {}", evento.ventaId(), nota);
    }
}
