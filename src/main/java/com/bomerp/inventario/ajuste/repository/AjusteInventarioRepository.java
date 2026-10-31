package com.bomerp.inventario.ajuste.repository;

import com.bomerp.inventario.ajuste.entity.AjusteInventario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface AjusteInventarioRepository extends JpaRepository<AjusteInventario, Long> {

    /** Ajustes con filtros opcionales, el más reciente primero. */
    @Query("""
            SELECT a FROM AjusteInventario a JOIN FETCH a.producto p
            WHERE (:productoId IS NULL OR p.id = :productoId)
              AND (:motivo IS NULL OR a.motivo = :motivo)
              AND (CAST(:desde AS LocalDate) IS NULL OR a.fecha >= :desde)
              AND (CAST(:hasta AS LocalDate) IS NULL OR a.fecha <= :hasta)
            ORDER BY a.fecha DESC, a.id DESC
            """)
    List<AjusteInventario> buscar(@Param("productoId") Long productoId, @Param("motivo") AjusteInventario.Motivo motivo,
                                  @Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

    /** Fachada para el kardex: ajustes de un producto. */
    @Query("SELECT a FROM AjusteInventario a WHERE a.producto.id = :productoId")
    List<AjusteInventario> deProducto(@Param("productoId") Long productoId);
}
