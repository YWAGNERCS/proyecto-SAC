package com.bomerp.compras.compra.repository;

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
