package com.bomerp.postventa.mantenimiento.repository;

import com.bomerp.postventa.mantenimiento.entity.Mantenimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface MantenimientoRepository extends JpaRepository<Mantenimiento, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.QueryHints(@jakarta.persistence.QueryHint(name = "jakarta.persistence.lock.timeout", value = "10000"))
    @Query("SELECT m FROM Mantenimiento m WHERE m.id = :id")
    java.util.Optional<Mantenimiento> bloquear(@Param("id") Long id);

    // Busca los mantenimientos que están pendientes, no se han enviado alertas, y faltan 7 días o menos.
    List<Mantenimiento> findByEstadoAndAlertaEnviadaFalseAndFechaProgramadaBetween(
            Mantenimiento.EstadoMantenimiento estado, LocalDate startDate, LocalDate endDate);

    List<Mantenimiento> findByVenta_IdAndEstado(Long ventaId, Mantenimiento.EstadoMantenimiento estado);

    @Query("SELECT m.id FROM Mantenimiento m WHERE m.venta.id = :ventaId AND m.estado = :estado ORDER BY m.id")
    List<Long> idsDeVenta(Long ventaId, Mantenimiento.EstadoMantenimiento estado);

    /** Los de la venta que no son de un equipo en particular (las de la rutina elegida al vender). */
    @Query("SELECT m.id FROM Mantenimiento m WHERE m.venta.id = :ventaId AND m.equipo IS NULL AND m.estado = :estado ORDER BY m.id")
    List<Long> idsDeVentaSinEquipo(Long ventaId, Mantenimiento.EstadoMantenimiento estado);

    @Query("SELECT m.id FROM Mantenimiento m WHERE m.equipo.id = :equipoId AND m.estado = :estado ORDER BY m.id")
    List<Long> idsDeEquipo(Long equipoId, Mantenimiento.EstadoMantenimiento estado);

    @Query("SELECT m.id FROM Mantenimiento m WHERE m.contrato.id = :contratoId AND m.estado = :estado ORDER BY m.id")
    List<Long> idsDeContrato(Long contratoId, Mantenimiento.EstadoMantenimiento estado);

    long countByContrato_IdAndEstado(Long contratoId, Mantenimiento.EstadoMantenimiento estado);

    /** Fecha de la última visita del equipo con ese estado (para seguir su plan desde ahí). */
    @Query("SELECT MAX(m.fechaProgramada) FROM Mantenimiento m WHERE m.equipo.id = :equipoId AND m.estado IN :estados")
    LocalDate ultimaFechaDeEquipo(@Param("equipoId") Long equipoId,
                                  @Param("estados") List<Mantenimiento.EstadoMantenimiento> estados);

    /** Próxima visita pendiente del equipo (null si no tiene). */
    @Query("SELECT MIN(m.fechaProgramada) FROM Mantenimiento m WHERE m.equipo.id = :equipoId "
            + "AND m.estado = com.bomerp.postventa.mantenimiento.entity.Mantenimiento.EstadoMantenimiento.PENDIENTE")
    LocalDate proximaDeEquipo(@Param("equipoId") Long equipoId);

    // Pantalla de Postventa: cada filtro es opcional (null = no filtra)
    @Query("""
            SELECT m FROM Mantenimiento m JOIN FETCH m.cliente c
            LEFT JOIN FETCH m.venta LEFT JOIN FETCH m.equipo e LEFT JOIN FETCH e.producto LEFT JOIN FETCH e.sede
            LEFT JOIN FETCH m.contrato k LEFT JOIN FETCH k.sede
            WHERE (:estado IS NULL OR m.estado = :estado)
              AND (CAST(:desde AS LocalDate) IS NULL OR m.fechaProgramada >= :desde)
              AND (CAST(:hasta AS LocalDate) IS NULL OR m.fechaProgramada <= :hasta)
              AND (:clienteId IS NULL OR c.id = :clienteId)
            ORDER BY m.fechaProgramada, m.id
            """)
    List<Mantenimiento> buscar(@Param("estado") Mantenimiento.EstadoMantenimiento estado,
                               @Param("desde") LocalDate desde,
                               @Param("hasta") LocalDate hasta,
                               @Param("clienteId") Long clienteId);
}
