package com.bomerp.ventas.venta.repository;
import com.bomerp.ventas.venta.entity.Venta;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface VentaRepository extends JpaRepository<Venta, Long> {
    List<Venta> findByEstadoPago(Venta.EstadoPago estadoPago);

    // Para el reporte de cobranza: ventas a crédito con pago vencido
    List<Venta> findByEstadoPagoAndFechaLimitePagoBefore(
            Venta.EstadoPago estadoPago, LocalDate fecha);

    List<Venta> findByFechaVentaBetween(LocalDate desde, LocalDate hasta);

    // Reporte de ventas: cada filtro es opcional (null = no filtra por ese campo)
    @Query("""
            SELECT v FROM Venta v
            WHERE (:estado IS NULL OR v.estado = :estado)
              AND (CAST(:desde AS LocalDate) IS NULL OR v.fechaVenta >= :desde)
              AND (CAST(:hasta AS LocalDate) IS NULL OR v.fechaVenta <= :hasta)
            ORDER BY v.fechaVenta DESC, v.id DESC
            """)
    List<Venta> buscar(@Param("estado") Venta.EstadoVenta estado,
                       @Param("desde") LocalDate desde,
                       @Param("hasta") LocalDate hasta);

    // Evita convertir dos veces la misma cotización en venta
    boolean existsByCotizacionOrigenId(Long cotizacionId);
}
