package com.bomerp.facturacion.notacredito.repository;

import com.bomerp.facturacion.notacredito.entity.NotaCredito;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotaCreditoRepository extends JpaRepository<NotaCredito, Long> {

    Optional<NotaCredito> findByComprobanteReferencia_Id(Long comprobanteId);

    /** Para el correlativo secuencial por serie (SUNAT exige numeración correlativa). */
    long countBySerie(String serie);

    List<NotaCredito> findAllByOrderByIdDesc();
}
