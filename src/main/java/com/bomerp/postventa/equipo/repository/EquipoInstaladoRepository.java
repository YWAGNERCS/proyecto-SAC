package com.bomerp.postventa.equipo.repository;

import com.bomerp.postventa.equipo.entity.EquipoInstalado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EquipoInstaladoRepository extends JpaRepository<EquipoInstalado, Long> {

    // El mismo número de serie no se registra dos veces para el mismo modelo (al editar se excluye el propio)
    boolean existsByProducto_IdAndNumeroSerieIgnoreCase(Long productoId, String numeroSerie);

    boolean existsByProducto_IdAndNumeroSerieIgnoreCaseAndIdNot(Long productoId, String numeroSerie, Long id);

    List<EquipoInstalado> findByOrdenTrabajoId(Long ordenTrabajoId);

    /** Parque instalado; cada filtro es opcional (null = no filtra). */
    @Query("""
            SELECT e FROM EquipoInstalado e JOIN FETCH e.cliente c JOIN FETCH e.producto LEFT JOIN FETCH e.sede s
            WHERE (:clienteId IS NULL OR c.id = :clienteId)
              AND (:sedeId IS NULL OR s.id = :sedeId)
              AND (:estado IS NULL OR e.estado = :estado)
            ORDER BY c.nombreORazonSocial, s.nombre, e.fechaInstalacion DESC, e.id DESC
            """)
    List<EquipoInstalado> buscar(@Param("clienteId") Long clienteId, @Param("sedeId") Long sedeId,
                                 @Param("estado") EquipoInstalado.Estado estado);
}
