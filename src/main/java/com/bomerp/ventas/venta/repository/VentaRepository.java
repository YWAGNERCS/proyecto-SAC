package com.bomerp.ventas.venta.repository;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.QueryHints;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import com.bomerp.ventas.venta.entity.Venta;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Sort;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public interface VentaRepository extends JpaRepository<Venta, Long>, JpaSpecificationExecutor<Venta> {
    // Las consultas de cobranza y reportes reciben el estado de la venta para excluir las ANULADAS
    List<Venta> findByEstadoAndEstadoPago(Venta.EstadoVenta estado, Venta.EstadoPago estadoPago);

    // Para el reporte de cobranza: ventas a crédito con pago vencido
    List<Venta> findByEstadoAndEstadoPagoAndFechaLimitePagoBefore(
            Venta.EstadoVenta estado, Venta.EstadoPago estadoPago, LocalDate fecha);

    List<Venta> findByEstadoAndFechaVentaBetween(Venta.EstadoVenta estado, LocalDate desde, LocalDate hasta);

    /**
     * Reporte de ventas: cada filtro es opcional (null = no filtra por ese campo).
     * BD2 S4: antes era una sola JPQL con "(:x IS NULL OR ...)" para los cuatro filtros. Oracle tenia que planear
     * para todos los casos a la vez y estimaba mal. Ahora el SQL solo lleva las condiciones que de verdad llegan.
     */
    default List<Venta> buscar(Venta.EstadoVenta estado, LocalDate desde, LocalDate hasta, Long clienteId) {
        Specification<Venta> filtros = (root, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            if (estado != null) {
                condiciones.add(cb.equal(root.get("estado"), estado));
            }
            if (desde != null) {
                condiciones.add(cb.greaterThanOrEqualTo(root.get("fechaVenta"), desde));
            }
            if (hasta != null) {
                condiciones.add(cb.lessThanOrEqualTo(root.get("fechaVenta"), hasta));
            }
            if (clienteId != null) {
                condiciones.add(cb.equal(root.get("cliente").get("id"), clienteId));
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
        return findAll(filtros, Sort.by(Sort.Direction.DESC, "fechaVenta", "id"));
    }

    // Kardex: líneas vendidas de un producto, con su venta (para fecha, cliente y anulación)
    @Query("""
            SELECT d FROM DetalleVenta d JOIN FETCH d.venta v JOIN FETCH v.cliente
            WHERE d.producto.id = :productoId
            ORDER BY v.fechaVenta, v.id
            """)
    List<com.bomerp.ventas.detalleventa.entity.DetalleVenta> detallesDeProducto(@Param("productoId") Long productoId);

    /**
     * Rentabilidad: líneas de las ventas no anuladas del periodo, con venta, cliente y producto.
     * BD2 S4: rango fijo (sin "IS NULL OR") y estado por igualdad. Oracle estima 2 688 filas frente a 2 664 reales,
     * contra 8 979 de la versión anterior. Sin fecha, el periodo queda abierto con límites que cubren todo.
     */
    default List<com.bomerp.ventas.detalleventa.entity.DetalleVenta> lineasVendidas(LocalDate desde, LocalDate hasta) {
        return lineasVendidasEntre(desde != null ? desde : LocalDate.of(1900, 1, 1),
                hasta != null ? hasta : LocalDate.of(9999, 12, 31));
    }

    @Query("""
            SELECT d FROM DetalleVenta d JOIN FETCH d.venta v JOIN FETCH v.cliente JOIN FETCH d.producto p
            LEFT JOIN FETCH p.categoria
            WHERE v.estado = com.bomerp.ventas.venta.entity.Venta.EstadoVenta.REGISTRADA
              AND v.fechaVenta >= :desde
              AND v.fechaVenta <= :hasta
            ORDER BY v.fechaVenta, v.id
            """)
    List<com.bomerp.ventas.detalleventa.entity.DetalleVenta> lineasVendidasEntre(@Param("desde") LocalDate desde,
                                                                               @Param("hasta") LocalDate hasta);

    /** Rentabilidad por técnico: líneas de las ventas que facturaron órdenes de trabajo. */
    @Query("""
            SELECT d FROM DetalleVenta d JOIN FETCH d.venta v JOIN FETCH d.producto
            WHERE v.id IN :ids AND v.estado <> com.bomerp.ventas.venta.entity.Venta.EstadoVenta.ANULADA
            """)
    List<com.bomerp.ventas.detalleventa.entity.DetalleVenta> lineasDeVentas(@Param("ids") java.util.Collection<Long> ids);

    /** Lee la venta bloqueando su fila hasta el fin de la transacción (cobros simultáneos). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "10000"))
    @Query("SELECT v FROM Venta v WHERE v.id = :id")
    java.util.Optional<Venta> bloquear(@Param("id") Long id);

    // Evita convertir dos veces la misma proforma en venta
    boolean existsByProformaOrigenId(Long proformaId);
}
