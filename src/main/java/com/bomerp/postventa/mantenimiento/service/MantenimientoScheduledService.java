package com.bomerp.postventa.mantenimiento.service;

import com.bomerp.postventa.mantenimiento.entity.Mantenimiento;
import com.bomerp.postventa.mantenimiento.repository.MantenimientoRepository;
import com.bomerp.notificaciones.notificacion.service.NotificadorCorreo;
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

    /** Correo de la oficina (app.notificaciones.correo-oficina); vacío = no se envía. */
    private final String correoOficina;

    public MantenimientoScheduledService(MantenimientoRepository mantenimientoRepository, NotificadorCorreo notificadorCorreo,
                                         @Value("${app.notificaciones.correo-oficina:}") String correoOficina) {
        this.mantenimientoRepository = mantenimientoRepository;
        this.notificadorCorreo = notificadorCorreo;
        this.correoOficina = correoOficina;
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
            // Solo se marca si el correo salió: si falla, se vuelve a intentar al día siguiente
            if (enviarAlertaAOficina(mantenimiento)) {
                mantenimiento.setAlertaEnviada(true);
                mantenimientoRepository.save(mantenimiento);
            }
        }
    }

    private boolean enviarAlertaAOficina(Mantenimiento mantenimiento) {
        String nombreCliente = mantenimiento.getCliente().getNombreORazonSocial();
        String telefonoCliente = mantenimiento.getCliente().getTelefono();
        
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

        if (correoOficina == null || correoOficina.isBlank()) {
            System.err.println("Alerta de mantenimiento no enviada: falta configurar CORREO_OFICINA.");
            return false;
        }
        boolean enviado = notificadorCorreo.enviarCorreo(correoOficina, asunto, cuerpoHtml);
        if (enviado) {
            System.out.println("Alerta de mantenimiento enviada a la oficina para el cliente: " + nombreCliente);
        }
        return enviado;
    }
}
