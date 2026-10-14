package com.bomerp.proformas.proforma.repository;
import com.bomerp.proformas.proforma.entity.Proforma;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ProformaRepository extends JpaRepository<Proforma, Long> {
    List<Proforma> findByEstado(Proforma.EstadoProforma estado);

    // Para el job que marca automáticamente las proformas vencidas
    List<Proforma> findByEstadoAndFechaVencimientoBefore(
            Proforma.EstadoProforma estado, LocalDate fecha);
}
