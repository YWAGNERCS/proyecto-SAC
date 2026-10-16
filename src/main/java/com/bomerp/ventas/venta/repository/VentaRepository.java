package com.bomerp.ventas.venta.repository;
import com.bomerp.ventas.venta.entity.Venta;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface VentaRepository extends JpaRepository<Venta, Long> {
    // Las consultas de cobranza y reportes reciben el estado de la venta para excluir las ANULADAS
    List<Venta> findByEstadoAndEstadoPago(Venta.EstadoVenta estado, Venta.EstadoPago estadoPago);

    // Para el reporte de cobranza: ventas a crédito con pago vencido
    List<Venta> findByEstadoAndEstadoPagoAndFechaLimitePagoBefore(
            Venta.EstadoVenta estado, Venta.EstadoPago estadoPago, LocalDate fecha);

    List<Venta> findByEstadoAndFechaVentaBetween(Venta.EstadoVenta estado, LocalDate desde, LocalDate hasta);

    // Reporte de ventas: cada filtro es opcional (null = no filtra por ese campo)
    @Query("""
            SELECT v FROM Venta v
            WHERE (:estado IS NULL OR v.estado = :estado)
              AND (CAST(:desde AS LocalDate) IS NULL OR v.fechaVenta >= :desde)
              AND (CAST(:hasta AS LocalDate) IS NULL OR v.fechaVenta <= :hasta)
              AND (:clienteId IS NULL OR v.cliente.id = :clienteId)
            ORDER BY v.fechaVenta DESC, v.id DESC
            """)
    List<Venta> buscar(@Param("estado") Venta.EstadoVenta estado,
                       @Param("desde") LocalDate desde,
                       @Param("hasta") LocalDate hasta,
                       @Param("clienteId") Long clienteId);

    // Kardex: líneas vendidas de un producto, con su venta (para fecha, cliente y anulación)
    @Query("""
            SELECT d FROM DetalleVenta d JOIN FETCH d.venta v JOIN FETCH v.cliente
            WHERE d.producto.id = :productoId
            ORDER BY v.fechaVenta, v.id
            """)
    List<com.bomerp.ventas.detalleventa.entity.DetalleVenta> detallesDeProducto(@Param("productoId") Long productoId);

    // Evita convertir dos veces la misma proforma en venta
    boolean existsByProformaOrigenId(Long proformaId);
}
