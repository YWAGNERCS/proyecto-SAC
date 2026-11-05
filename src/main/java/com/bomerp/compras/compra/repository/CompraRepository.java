package com.bomerp.compras.compra.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.QueryHints;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import com.bomerp.compras.detallecompra.entity.DetalleCompra;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import com.bomerp.compras.compra.entity.Compra;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompraRepository extends JpaRepository<Compra, Long> {

    /** Lee la compra bloqueando su fila hasta el fin de la transacción (pagos simultáneos). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "10000"))
    @Query("SELECT c FROM Compra c WHERE c.id = :id")
    java.util.Optional<Compra> bloquear(@Param("id") Long id);

    @EntityGraph(attributePaths = {"proveedor", "detalles", "detalles.producto"})
    List<Compra> findAll();

    @EntityGraph(attributePaths = {"proveedor", "detalles", "detalles.producto"})
    Optional<Compra> findById(Long id);

    // Reporte e historial: cada filtro es opcional (null = no filtra)
    @EntityGraph(attributePaths = {"proveedor", "detalles", "detalles.producto"})
    @Query("""
            SELECT c FROM Compra c
            WHERE (CAST(:desde AS LocalDate) IS NULL OR c.fechaCompra >= :desde)
              AND (CAST(:hasta AS LocalDate) IS NULL OR c.fechaCompra <= :hasta)
              AND (:proveedorId IS NULL OR c.proveedor.id = :proveedorId)
            ORDER BY c.fechaCompra DESC, c.id DESC
            """)
    List<Compra> buscar(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta,
                        @Param("proveedorId") Long proveedorId);

    /** Lo mismo, por páginas (la pantalla de Compras). */
    @Query(value = """
            SELECT c FROM Compra c JOIN FETCH c.proveedor
            WHERE (CAST(:desde AS LocalDate) IS NULL OR c.fechaCompra >= :desde)
              AND (CAST(:hasta AS LocalDate) IS NULL OR c.fechaCompra <= :hasta)
              AND (:proveedorId IS NULL OR c.proveedor.id = :proveedorId)
            ORDER BY c.fechaCompra DESC, c.id DESC
            """, countQuery = """
            SELECT COUNT(c) FROM Compra c
            WHERE (CAST(:desde AS LocalDate) IS NULL OR c.fechaCompra >= :desde)
              AND (CAST(:hasta AS LocalDate) IS NULL OR c.fechaCompra <= :hasta)
              AND (:proveedorId IS NULL OR c.proveedor.id = :proveedorId)
            """)
    Page<Compra> buscarPagina(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta,
                              @Param("proveedorId") Long proveedorId, Pageable pedido);

    // Kardex: líneas compradas de un producto
    @Query("""
            SELECT d FROM DetalleCompra d JOIN FETCH d.compra c JOIN FETCH c.proveedor
            WHERE d.producto.id = :productoId
            ORDER BY c.fechaCompra, c.id
            """)
    List<DetalleCompra> detallesDeProducto(@Param("productoId") Long productoId);

    // Evita registrar dos veces la misma factura del mismo proveedor
    Optional<Compra> findFirstByProveedor_IdAndNumeroFactura(Long proveedorId, String numeroFactura);
}
