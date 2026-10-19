package com.bomerp.cobranza.cobro.repository;

import com.bomerp.cobranza.cobro.entity.Cobro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public interface CobroRepository extends JpaRepository<Cobro, Long> {

    /** Total abonado a una venta. */
    @Query("SELECT COALESCE(SUM(d.monto), 0) FROM DetalleCobro d WHERE d.venta.id = :ventaId")
    BigDecimal totalCobradoDeVenta(@Param("ventaId") Long ventaId);

    /** Total abonado por venta, para varias ventas a la vez: filas [ventaId, suma]. */
    @Query("SELECT d.venta.id, SUM(d.monto) FROM DetalleCobro d WHERE d.venta.id IN :ventaIds GROUP BY d.venta.id")
    List<Object[]> totalesCobradosPorVenta(@Param("ventaIds") Collection<Long> ventaIds);

    /** Historial de cobros, con filtros opcionales por cliente o por venta. */
    @Query("""
            SELECT DISTINCT c FROM Cobro c JOIN FETCH c.cliente
            LEFT JOIN FETCH c.detalles d LEFT JOIN FETCH d.venta
            WHERE (:clienteId IS NULL OR c.cliente.id = :clienteId)
              AND (:ventaId IS NULL OR EXISTS (
                    SELECT 1 FROM DetalleCobro x WHERE x.cobro = c AND x.venta.id = :ventaId))
            ORDER BY c.fecha DESC, c.id DESC
            """)
    List<Cobro> buscar(@Param("clienteId") Long clienteId, @Param("ventaId") Long ventaId);
}
