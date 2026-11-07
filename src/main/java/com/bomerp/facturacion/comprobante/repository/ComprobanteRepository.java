package com.bomerp.facturacion.comprobante.repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.bomerp.facturacion.comprobante.entity.Comprobante;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ComprobanteRepository extends JpaRepository<Comprobante, Long> {
    Optional<Comprobante> findByVentaId(Long ventaId);

    List<Comprobante> findByVenta_IdIn(List<Long> ventaIds);

    /** Último número emitido en una serie (los números tienen 8 dígitos, así que MAX de texto sirve). */
    /** Pendientes de envío a SUNAT, los más antiguos primero (el reintento los toma de a pocos). */
    List<Comprobante> findTop20ByEstadoSunatOrderByIdAsc(Comprobante.EstadoSunat estado);

    @Query("SELECT MAX(c.numero) FROM Comprobante c WHERE c.serie = :serie")
    String maxNumeroDeSerie(@Param("serie") String serie);
}
