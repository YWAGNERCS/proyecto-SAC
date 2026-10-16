package com.bomerp.proformas.proforma.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Vence automáticamente las proformas PENDIENTES cuya fecha de vencimiento ya pasó.
 *  - Todos los días a las 00:05.
 *  - También al iniciar el backend: si el servidor estuvo apagado a esa hora
 *    (lo normal en desarrollo), las proformas igual quedan al día.
 */
@Service
@Slf4j
public class ProformaScheduledService {

    private final ProformaService proformaService;

    public ProformaScheduledService(ProformaService proformaService) {
        this.proformaService = proformaService;
    }

    @Scheduled(cron = "0 5 0 * * ?")
    public void vencerProformasDiario() {
        ejecutar("tarea diaria");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void vencerProformasAlIniciar() {
        ejecutar("inicio del sistema");
    }

    private void ejecutar(String origen) {
        try {
            int vencidas = proformaService.marcarVencidas();
            log.info("Proformas vencidas ({}): {}", origen, vencidas);
        } catch (Exception e) {
            // No debe impedir que el sistema arranque ni detener el resto de tareas programadas
            log.error("No se pudieron vencer las proformas ({}): {}", origen, e.getMessage());
        }
    }
}
