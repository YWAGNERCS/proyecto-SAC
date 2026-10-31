package com.bomerp.compras.pago.repository;

import com.bomerp.compras.compra.entity.Compra;
import com.bomerp.compras.pago.entity.PagoProveedor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface PagoProveedorRepository extends JpaRepository<PagoProveedor, Long> {

    /** Lo pagado (pagos vigentes) de una compra. */
    @Query("""
            SELECT COALESCE(SUM(p.monto), 0) FROM PagoProveedor p
            WHERE p.compra.id = :compraId AND p.estado = com.bomerp.compras.pago.entity.PagoProveedor.Estado.VIGENTE
            """)
    BigDecimal pagadoDeCompra(@Param("compraId") Long compraId);

    /** Lo pagado por compra, de varias a la vez: filas [compraId, suma]. */
    @Query("""
            SELECT p.compra.id, SUM(p.monto) FROM PagoProveedor p
            WHERE p.compra.id IN :ids AND p.estado = com.bomerp.compras.pago.entity.PagoProveedor.Estado.VIGENTE
            GROUP BY p.compra.id
            """)
    List<Object[]> pagadoPorCompra(@Param("ids") Collection<Long> ids);

    /** Compras a crédito con saldo, la que vence primero arriba. */
    @Query("""
            SELECT c FROM Compra c JOIN FETCH c.proveedor
            WHERE c.tipoPago = com.bomerp.compras.compra.entity.Compra.TipoPago.CREDITO
              AND c.estadoPago = com.bomerp.compras.compra.entity.Compra.EstadoPago.PENDIENTE
              AND (:proveedorId IS NULL OR c.proveedor.id = :proveedorId)
            ORDER BY c.fechaVencimiento, c.id
            """)
    List<Compra> comprasPendientes(@Param("proveedorId") Long proveedorId);

    /** Historial de pagos con filtros opcionales, el más reciente primero. */
    @Query("""
            SELECT p FROM PagoProveedor p JOIN FETCH p.compra c JOIN FETCH c.proveedor
            WHERE (:proveedorId IS NULL OR c.proveedor.id = :proveedorId)
              AND (CAST(:desde AS LocalDate) IS NULL OR p.fechaPago >= :desde)
              AND (CAST(:hasta AS LocalDate) IS NULL OR p.fechaPago <= :hasta)
            ORDER BY p.fechaPago DESC, p.id DESC
            """)
    List<PagoProveedor> buscar(@Param("proveedorId") Long proveedorId, @Param("desde") LocalDate desde,
                               @Param("hasta") LocalDate hasta);

    /** Pagos de una compra (vigentes y anulados), en orden. */
    @Query("SELECT p FROM PagoProveedor p WHERE p.compra.id = :compraId ORDER BY p.fechaPago, p.id")
    List<PagoProveedor> deCompra(@Param("compraId") Long compraId);

    /** El mismo N° de operación con el mismo método a un mismo proveedor (pago duplicado). */
    @Query("""
            SELECT p FROM PagoProveedor p
            WHERE p.compra.proveedor.id = :proveedorId AND p.metodo = :metodo
              AND p.estado = com.bomerp.compras.pago.entity.PagoProveedor.Estado.VIGENTE
              AND UPPER(REPLACE(p.numeroOperacion, ' ', '')) = :numero
            """)
    List<PagoProveedor> mismaOperacion(@Param("proveedorId") Long proveedorId, @Param("metodo") PagoProveedor.Metodo metodo,
                                       @Param("numero") String numero);
}
