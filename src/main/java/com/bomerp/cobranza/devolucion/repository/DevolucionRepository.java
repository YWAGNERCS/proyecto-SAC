package com.bomerp.cobranza.devolucion.repository;

import com.bomerp.cobranza.devolucion.entity.Devolucion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DevolucionRepository extends JpaRepository<Devolucion, Long> {

    boolean existsByVentaId(Long ventaId);

    /** Devoluciones con filtros opcionales; primero las pendientes y las más recientes. */
    @Query("""
            SELECT d FROM Devolucion d JOIN FETCH d.cliente JOIN FETCH d.venta
            WHERE (:estado IS NULL OR d.estado = :estado)
              AND (:clienteId IS NULL OR d.cliente.id = :clienteId)
            ORDER BY d.estado DESC, d.fechaAnulacion DESC, d.id DESC
            """)
    List<Devolucion> buscar(@Param("estado") Devolucion.Estado estado, @Param("clienteId") Long clienteId);
}
