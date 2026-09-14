package com.jdrefrigeracion.inventario.services;

import com.jdrefrigeracion.catalogo.models.Producto;
import com.jdrefrigeracion.notificaciones.services.NotificadorCorreo;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class InventarioScheduledService {

    private final InventarioService inventarioService;
    private final NotificadorCorreo notificadorCorreo;

    private final String correoOficina = "yoelwagnercs@gmail.com";

    public InventarioScheduledService(InventarioService inventarioService, NotificadorCorreo notificadorCorreo) {
        this.inventarioService = inventarioService;
        this.notificadorCorreo = notificadorCorreo;
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
            System.out.println("No hay productos en estado crítico de stock.");
            return;
        }

        enviarReporteAOficina(productosCriticos);
    }

    private void enviarReporteAOficina(List<Producto> productos) {
        String asunto = "¡Alerta de Inventario! " + productos.size() + " productos con bajo stock";
        
        StringBuilder filas = new StringBuilder();
        for (Producto p : productos) {
            filas.append("<tr>")
                 .append("<td style='padding: 8px; border: 1px solid #ddd;'>").append(p.getId()).append("</td>")
                 .append("<td style='padding: 8px; border: 1px solid #ddd;'>").append(p.getNombre()).append("</td>")
                 .append("<td style='padding: 8px; border: 1px solid #ddd; color: red; font-weight: bold;'>").append(p.getStockActual() != null ? p.getStockActual() : 0).append("</td>")
                 .append("<td style='padding: 8px; border: 1px solid #ddd;'>").append(p.getStockMinimo() != null ? p.getStockMinimo() : 0).append("</td>")
                 .append("</tr>");
        }

        String cuerpoHtml = "<!DOCTYPE html><html><body style='font-family: Arial, sans-serif; background-color: #f4f4f4; padding: 20px;'>" +
                            "<div style='max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 8px; padding: 30px; border-left: 5px solid #d32f2f;'>" +
                            "<h2 style='color: #d32f2f;'>Reporte de Stock Mínimo</h2>" +
                            "<p>Hola,</p>" +
                            "<p>El sistema ha detectado que los siguientes productos han caído por debajo de su nivel de stock mínimo. Por favor, gestione las compras con sus proveedores a la brevedad.</p>" +
                            "<table style='width: 100%; border-collapse: collapse; margin-top: 20px;'>" +
                            "<thead><tr style='background-color: #f8f8f8;'>" +
                            "<th style='padding: 8px; border: 1px solid #ddd; text-align: left;'>ID</th>" +
                            "<th style='padding: 8px; border: 1px solid #ddd; text-align: left;'>Producto</th>" +
                            "<th style='padding: 8px; border: 1px solid #ddd; text-align: left;'>Stock Actual</th>" +
                            "<th style='padding: 8px; border: 1px solid #ddd; text-align: left;'>Stock Mínimo</th>" +
                            "</tr></thead>" +
                            "<tbody>" + filas.toString() + "</tbody>" +
                            "</table>" +
                            "<hr style='border: none; border-top: 1px solid #eee; margin: 20px 0;'/>" +
                            "<p style='color: #888; font-size: 12px;'>Este es un mensaje generado automáticamente por el Sistema de JD Refrigeración.</p>" +
                            "</div></body></html>";

        notificadorCorreo.enviarCorreo(correoOficina, asunto, cuerpoHtml);
        System.out.println("Reporte de inventario enviado a la oficina.");
    }
}
