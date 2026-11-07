package com.bomerp.postventa.mantenimiento.service;

import com.bomerp.ventas.venta.event.VentaAnuladaEvent;
import com.bomerp.ventas.venta.event.VentaRegistradaEvent;
import com.bomerp.ventas.venta.service.VentaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Postventa reacciona a lo que pasa en Ventas (ADR-002: Ventas publica, Postventa escucha).
 * Corre dentro de la transacción de la venta: si falla, la venta tampoco se guarda.
 */
@Component
@Slf4j
public class VentasPostventaListener {

    private final VentaService ventaService;
    private final MantenimientoService mantenimientoService;

    public VentasPostventaListener(VentaService ventaService, MantenimientoService mantenimientoService) {
        this.ventaService = ventaService;
        this.mantenimientoService = mantenimientoService;
    }

    /** Si el vendedor eligió una rutina de mantenimiento al vender, se programan sus visitas. */
    @EventListener
    @Order(1)
    public void alRegistrarVenta(VentaRegistradaEvent evento) {
        if (evento.rutina() == null) return;
        mantenimientoService.programarParaVenta(ventaService.obtenerPorIdPublico(evento.ventaId()), evento.rutina());
    }

    /** Venta anulada: se cancelan sus visitas aún pendientes. */
    @EventListener
    @Order(1)
    public void alAnularVenta(VentaAnuladaEvent evento) {
        int canceladas = mantenimientoService.cancelarPendientesDeVenta(evento.ventaId());
        log.info("Venta {} anulada: {} mantenimientos cancelados", evento.ventaId(), canceladas);
    }
}
