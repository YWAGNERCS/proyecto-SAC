package com.jdrefrigeracion.cotizaciones.repositories;
import com.jdrefrigeracion.cotizaciones.models.Cotizacion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface CotizacionRepository extends JpaRepository<Cotizacion, Long> {
    List<Cotizacion> findByEstado(Cotizacion.EstadoCotizacion estado);

    // Para el job que marca automáticamente las cotizaciones vencidas
    List<Cotizacion> findByEstadoAndFechaVencimientoBefore(
            Cotizacion.EstadoCotizacion estado, LocalDate fecha);
}
