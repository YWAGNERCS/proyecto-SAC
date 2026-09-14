package com.jdrefrigeracion.mantenimiento.repositories;

import com.jdrefrigeracion.mantenimiento.models.Mantenimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface MantenimientoRepository extends JpaRepository<Mantenimiento, Long> {
    
    // Busca los mantenimientos que están pendientes, no se han enviado alertas, y faltan 7 días o menos.
    List<Mantenimiento> findByEstadoAndAlertaEnviadaFalseAndFechaProgramadaBetween(
            Mantenimiento.EstadoMantenimiento estado, LocalDate startDate, LocalDate endDate);
}
