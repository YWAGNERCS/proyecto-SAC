package com.bomerp.servicios.orden.repository;

import com.bomerp.servicios.orden.entity.MaterialOrden;
import com.bomerp.servicios.orden.entity.OrdenTrabajo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface OrdenTrabajoRepository extends JpaRepository<OrdenTrabajo, Long> {

    /** Una visita de mantenimiento no tiene dos órdenes programadas a la vez. */
    boolean existsByMantenimientoIdAndEstado(Long mantenimientoId, OrdenTrabajo.Estado estado);

    /** Órdenes; cada filtro es opcional (null = no filtra). El técnico se busca por parte del nombre. */
    @Query("""
            SELECT o FROM OrdenTrabajo o JOIN FETCH o.cliente c LEFT JOIN FETCH o.sede
            WHERE (:estado IS NULL OR o.estado = :estado)
              AND (:clienteId IS NULL OR c.id = :clienteId)
              AND (:tecnico IS NULL OR LOWER(o.tecnico) LIKE LOWER(CONCAT('%', :tecnico, '%')))
              AND (CAST(:desde AS LocalDate) IS NULL OR o.fechaProgramada >= :desde)
              AND (CAST(:hasta AS LocalDate) IS NULL OR o.fechaProgramada <= :hasta)
            ORDER BY o.fechaProgramada DESC, o.id DESC
            """)
    List<OrdenTrabajo> buscar(@Param("estado") OrdenTrabajo.Estado estado, @Param("clienteId") Long clienteId,
                              @Param("tecnico") String tecnico, @Param("desde") LocalDate desde,
                              @Param("hasta") LocalDate hasta);

    /** Fachada para el kardex: materiales de un producto usados en órdenes REALIZADAS. */
    @Query("""
            SELECT m FROM MaterialOrden m JOIN FETCH m.orden o JOIN FETCH o.cliente
            WHERE m.producto.id = :productoId
              AND o.estado = com.bomerp.servicios.orden.entity.OrdenTrabajo.Estado.REALIZADA
            """)
    List<MaterialOrden> materialesUsadosDeProducto(@Param("productoId") Long productoId);

    /** Técnicos que ya tienen órdenes (para sugerirlos al programar una nueva). */
    @Query("SELECT DISTINCT o.tecnico FROM OrdenTrabajo o ORDER BY o.tecnico")
    List<String> tecnicos();
}
