package com.bomerp.facturacion.serie.repository;

import com.bomerp.facturacion.serie.entity.SerieComprobante;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SerieComprobanteRepository extends JpaRepository<SerieComprobante, String> {

    /**
     * Lee el contador con bloqueo de escritura (SELECT ... FOR UPDATE): si otra venta está
     * emitiendo en la misma serie, esta espera a que termine. Así dos comprobantes nunca
     * reciben el mismo número.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SerieComprobante s WHERE s.serie = :serie")
    Optional<SerieComprobante> bloquear(@Param("serie") String serie);
}
