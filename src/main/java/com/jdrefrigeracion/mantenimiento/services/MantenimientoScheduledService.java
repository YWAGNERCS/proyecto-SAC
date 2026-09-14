package com.jdrefrigeracion.mantenimiento.services;

import com.jdrefrigeracion.mantenimiento.models.Mantenimiento;
import com.jdrefrigeracion.mantenimiento.repositories.MantenimientoRepository;
import com.jdrefrigeracion.notificaciones.services.NotificadorCorreo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class MantenimientoScheduledService {

    private final MantenimientoRepository mantenimientoRepository;
    private final NotificadorCorreo notificadorCorreo;

    // Hardcodeado temporalmente al correo de la secretaria/oficina
    private final String correoOficina = "yoelwagnercs@gmail.com"; 

    public MantenimientoScheduledService(MantenimientoRepository mantenimientoRepository, NotificadorCorreo notificadorCorreo) {
        this.mantenimientoRepository = mantenimientoRepository;
        this.notificadorCorreo = notificadorCorreo;
    }

    /**
     * Este Cron Job se ejecuta todos los días a las 08:00 AM.
     * Busca mantenimientos próximos (a 7 días de distancia) para avisar a la oficina.
     */
    @Scheduled(cron = "0 0 8 * * ?")
    @Transactional
    public void procesarAlertasMantenimientoDiario() {
        procesarAlertas();
    }

    public void procesarAlertas() {
        // Buscamos mantenimientos cuya fecha programada sea hoy o hasta dentro de 7 días
        LocalDate hoy = LocalDate.now();
        LocalDate limiteSieteDias = hoy.plusDays(7);

        List<Mantenimiento> proximos = mantenimientoRepository.findByEstadoAndAlertaEnviadaFalseAndFechaProgramadaBetween(
                Mantenimiento.EstadoMantenimiento.PENDIENTE, hoy, limiteSieteDias);

        for (Mantenimiento mantenimiento : proximos) {
            enviarAlertaAOficina(mantenimiento);
            mantenimiento.setAlertaEnviada(true);
            mantenimientoRepository.save(mantenimiento);
        }
    }

    private void enviarAlertaAOficina(Mantenimiento mantenimiento) {
        String nombreCliente = mantenimiento.getVenta().getCliente().getNombreORazonSocial();
        String telefonoCliente = mantenimiento.getVenta().getCliente().getTelefono();
        
        String asunto = "¡Alerta de Mantenimiento! Cliente: " + nombreCliente;
        String cuerpoHtml = "<!DOCTYPE html><html><body style='font-family: Arial, sans-serif; background-color: #f4f4f4; padding: 20px;'>" +
                            "<div style='max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 8px; padding: 30px; border-left: 5px solid #ff9800;'>" +
                            "<h2 style='color: #ff9800;'>Mantenimiento Próximo a Vencer</h2>" +
                            "<p>Hola,</p>" +
                            "<p>El sistema automático ha detectado que se acerca la fecha de mantenimiento preventivo para el siguiente cliente:</p>" +
                            "<ul>" +
                            "<li><strong>Cliente:</strong> " + nombreCliente + "</li>" +
                            "<li><strong>Teléfono:</strong> " + (telefonoCliente != null ? telefonoCliente : "No registrado") + "</li>" +
                            "<li><strong>Fecha Programada:</strong> " + mantenimiento.getFechaProgramada() + "</li>" +
                            "</ul>" +
                            "<p>Por favor, comuníquese con el cliente a la brevedad para agendar la visita técnica y generar la orden de servicio.</p>" +
                            "<hr style='border: none; border-top: 1px solid #eee; margin: 20px 0;'/>" +
                            "<p style='color: #888; font-size: 12px;'>Este es un mensaje generado automáticamente por el Sistema de JD Refrigeración.</p>" +
                            "</div></body></html>";

        notificadorCorreo.enviarCorreo(correoOficina, asunto, cuerpoHtml);
        System.out.println("Alerta de mantenimiento enviada a la oficina para el cliente: " + nombreCliente);
    }
}
