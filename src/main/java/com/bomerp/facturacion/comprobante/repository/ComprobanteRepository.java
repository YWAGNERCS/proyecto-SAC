package com.bomerp.facturacion.comprobante.repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.bomerp.facturacion.comprobante.entity.Comprobante;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ComprobanteRepository extends JpaRepository<Comprobante, Long> {
    Optional<Comprobante> findByVentaId(Long ventaId);

    /** Último número emitido en una serie (los números tienen 8 dígitos, así que MAX de texto sirve). */
    @Query("SELECT MAX(c.numero) FROM Comprobante c WHERE c.serie = :serie")
    String maxNumeroDeSerie(@Param("serie") String serie);
}
