package com.bomerp.inventario.inventario.service;

import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.notificaciones.notificacion.service.NotificadorCorreo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class InventarioScheduledService {

    private final InventarioService inventarioService;
    private final NotificadorCorreo notificadorCorreo;

    /** Correo de la oficina (app.notificaciones.correo-oficina); vacío = no se envía. */
    private final String correoOficina;

    public InventarioScheduledService(InventarioService inventarioService, NotificadorCorreo notificadorCorreo,
                                      @Value("${app.notificaciones.correo-oficina:}") String correoOficina) {
        this.inventarioService = inventarioService;
        this.notificadorCorreo = notificadorCorreo;
        this.correoOficina = correoOficina;
    }

    /**
     * Este Cron Job se ejecuta todos los días a las 08:00 AM.
     */
    @Scheduled(cron = "0 0 8 * * ?")
    @Transactional
    public void procesarReporteStockDiario() {
        procesarReporte();
    }

    public void procesarReporte() {
        List<Producto> productosCriticos = inventarioService.obtenerProductosEnAlerta();

        if (productosCriticos.isEmpty()) {
            log.info("No hay productos en estado crítico de stock.");
            return;
        }

        enviarReporteAOficina(productosCriticos);
    }

    private void enviarReporteAOficina(List<Producto> productos) {
        if (correoOficina == null || correoOficina.isBlank()) {
            log.debug("Reporte de inventario omitido: falta el correo de la oficina.");
            return;
        }
        int cantidad = productos.size();
        String asunto = cantidad == 1 ? "Alerta de inventario · 1 producto en el mínimo o por debajo"
                : "Alerta de inventario · " + cantidad + " productos en el mínimo o por debajo";
        boolean enviado = notificadorCorreo.enviarCorreo(correoOficina, asunto, ReporteStockCorreo.generar(productos));
        if (enviado) {
            log.info("Reporte de inventario enviado a la oficina.");
        } else {
            log.warn("No se pudo enviar el reporte de inventario a la oficina.");
        }
    }
}
