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

    /**
     * Registra el comprobante (PENDIENTE): se envía a SUNAT al confirmarse la venta.
     * Corre en la transacción de la venta, así que si no se puede crear, la venta tampoco se guarda: nunca
     * queda una venta sin comprobante. Antes un try/catch aparentaba lo contrario ("un error no aborta la
     * venta"), pero la transacción ya estaba marcada para deshacerse y el usuario recibía un error genérico
     * en lugar del motivo (revisión externa F-03). Ahora el error sube con su mensaje.
     */
    @EventListener
    @Order(2)
    public void alRegistrarVenta(VentaRegistradaEvent evento) {
        Comprobante comp = facturacionService.emitirComprobanteParaVenta(evento.ventaId());
        log.info("Comprobante {}-{} de la Venta {} registrado; se envía a SUNAT en segundo plano",
                comp.getSerie(), comp.getNumero(), evento.ventaId());
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

    /**
     * Protección del envío (EnvioSunatService): un comprobante de una venta anulada iba a salir sin su nota
     * de crédito. Se crea aquí, dentro de la transacción del envío; si falla, el comprobante no se envía.
     */
    @EventListener
    public void alEnviarComprobanteDeVentaAnulada(EnvioSunatService.VentaAnuladaSinNotaEvent evento) {
        facturacionService.emitirNotaCreditoPorAnulacion(evento.ventaId(), "Anulación de la operación (venta #" + evento.ventaId() + ")")
                .ifPresent(nc -> log.info("Venta {} anulada: nota de crédito {}-{} creada al enviar su comprobante",
                        evento.ventaId(), nc.getSerie(), nc.getNumero()));
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
