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

    /** Emite el comprobante y lo envía por correo si el cliente tiene uno; los errores no abortan la venta. */
    @EventListener
    @Order(2)
    public void alRegistrarVenta(VentaRegistradaEvent evento) {
        Comprobante comp;
        try {
            comp = facturacionService.emitirComprobanteParaVenta(evento.ventaId());
        } catch (Exception e) {
            log.error("Error al emitir el comprobante de la Venta {}: {}", evento.ventaId(), e.getMessage());
            return;
        }
        // Sin correo no es un error: el comprobante queda emitido y se descarga o reenvía desde Ventas
        String correo = comp.getVenta().getCliente().getCorreo();
        if (correo == null || correo.isBlank()) {
            log.info("Comprobante de la Venta {} emitido; no se envía por correo: el cliente no tiene correo", evento.ventaId());
            return;
        }
        try {
            facturacionService.enviarFacturaCorreo(comp.getId());
        } catch (Exception e) {
            log.warn("Comprobante de la Venta {} emitido, pero no se pudo enviar por correo: {}", evento.ventaId(), e.getMessage());
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
