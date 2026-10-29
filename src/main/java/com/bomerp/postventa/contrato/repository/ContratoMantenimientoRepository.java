package com.bomerp.postventa.contrato.repository;

import com.bomerp.postventa.contrato.entity.ContratoMantenimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ContratoMantenimientoRepository extends JpaRepository<ContratoMantenimiento, Long> {

    /** Contratos; filtros opcionales por cliente y estado (null = no filtra). */
    @Query("""
            SELECT k FROM ContratoMantenimiento k JOIN FETCH k.cliente c LEFT JOIN FETCH k.sede
            WHERE (:clienteId IS NULL OR c.id = :clienteId)
              AND (:estado IS NULL OR k.estado = :estado)
            ORDER BY k.fechaInicio DESC, k.id DESC
            """)
    List<ContratoMantenimiento> buscar(@Param("clienteId") Long clienteId,
                                       @Param("estado") ContratoMantenimiento.Estado estado);
}
