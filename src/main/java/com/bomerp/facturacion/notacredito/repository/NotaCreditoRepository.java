package com.bomerp.facturacion.notacredito.repository;

import com.bomerp.facturacion.comprobante.entity.Comprobante;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.bomerp.facturacion.notacredito.entity.NotaCredito;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotaCreditoRepository extends JpaRepository<NotaCredito, Long> {

    Optional<NotaCredito> findByComprobanteReferencia_Id(Long comprobanteId);

    /** Último número emitido en una serie de notas de crédito (FC01 / BC01). */
    /** Pendientes de envío a SUNAT, los más antiguos primero (el reintento los toma de a pocos). */
    List<NotaCredito> findTop20ByEstadoSunatOrderByIdAsc(Comprobante.EstadoSunat estado);

    @Query("SELECT MAX(n.numero) FROM NotaCredito n WHERE n.serie = :serie")
    String maxNumeroDeSerie(@Param("serie") String serie);

    List<NotaCredito> findAllByOrderByIdDesc();
}
