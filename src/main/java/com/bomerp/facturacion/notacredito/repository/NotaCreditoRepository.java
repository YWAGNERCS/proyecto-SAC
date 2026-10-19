package com.bomerp.facturacion.notacredito.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.bomerp.facturacion.notacredito.entity.NotaCredito;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotaCreditoRepository extends JpaRepository<NotaCredito, Long> {

    Optional<NotaCredito> findByComprobanteReferencia_Id(Long comprobanteId);

    /** Último número emitido en una serie de notas de crédito (FC01 / BC01). */
    @Query("SELECT MAX(n.numero) FROM NotaCredito n WHERE n.serie = :serie")
    String maxNumeroDeSerie(@Param("serie") String serie);

    List<NotaCredito> findAllByOrderByIdDesc();
}
