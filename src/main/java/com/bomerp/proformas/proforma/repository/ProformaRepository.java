package com.bomerp.proformas.proforma.repository;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.QueryHints;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.bomerp.proformas.proforma.entity.Proforma;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ProformaRepository extends JpaRepository<Proforma, Long> {

    /** Proformas por páginas, la más nueva primero; estado y cliente opcionales (null = no filtra). */
    @Query(value = """
            SELECT p FROM Proforma p JOIN FETCH p.cliente
            WHERE (:estado IS NULL OR p.estado = :estado) AND (:clienteId IS NULL OR p.cliente.id = :clienteId)
            ORDER BY p.id DESC
            """, countQuery = """
            SELECT COUNT(p) FROM Proforma p
            WHERE (:estado IS NULL OR p.estado = :estado) AND (:clienteId IS NULL OR p.cliente.id = :clienteId)
            """)
    org.springframework.data.domain.Page<Proforma> buscarPagina(@Param("estado") Proforma.EstadoProforma estado,
                                                                @Param("clienteId") Long clienteId,
                                                                org.springframework.data.domain.Pageable pedido);

    /** Lee la proforma bloqueando su fila hasta el fin de la transacción (conversión en venta). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "10000"))
    @Query("SELECT p FROM Proforma p WHERE p.id = :id")
    java.util.Optional<Proforma> bloquear(@Param("id") Long id);
    List<Proforma> findByEstado(Proforma.EstadoProforma estado);

    // Para el job que marca automáticamente las proformas vencidas
    List<Proforma> findByEstadoAndFechaVencimientoBefore(
            Proforma.EstadoProforma estado, LocalDate fecha);
}
