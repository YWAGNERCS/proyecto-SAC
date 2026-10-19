package com.bomerp.postventa.mantenimiento.repository;

import com.bomerp.postventa.mantenimiento.entity.Mantenimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface MantenimientoRepository extends JpaRepository<Mantenimiento, Long> {
    
    // Busca los mantenimientos que están pendientes, no se han enviado alertas, y faltan 7 días o menos.
    List<Mantenimiento> findByEstadoAndAlertaEnviadaFalseAndFechaProgramadaBetween(
            Mantenimiento.EstadoMantenimiento estado, LocalDate startDate, LocalDate endDate);

    List<Mantenimiento> findByVenta_IdAndEstado(Long ventaId, Mantenimiento.EstadoMantenimiento estado);

    // Pantalla de Postventa: cada filtro es opcional (null = no filtra)
    @org.springframework.data.jpa.repository.Query("""
            SELECT m FROM Mantenimiento m JOIN FETCH m.venta v JOIN FETCH v.cliente c
            WHERE (:estado IS NULL OR m.estado = :estado)
              AND (CAST(:desde AS LocalDate) IS NULL OR m.fechaProgramada >= :desde)
              AND (CAST(:hasta AS LocalDate) IS NULL OR m.fechaProgramada <= :hasta)
              AND (:clienteId IS NULL OR c.id = :clienteId)
            ORDER BY m.fechaProgramada, m.id
            """)
    List<Mantenimiento> buscar(@org.springframework.data.repository.query.Param("estado") Mantenimiento.EstadoMantenimiento estado,
                               @org.springframework.data.repository.query.Param("desde") LocalDate desde,
                               @org.springframework.data.repository.query.Param("hasta") LocalDate hasta,
                               @org.springframework.data.repository.query.Param("clienteId") Long clienteId);
}
