package com.bomerp.catalogo.producto.repository;
import com.bomerp.catalogo.producto.entity.Producto;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    // Solo lo que debe verse en el catálogo público
    List<Producto> findByVisibleEnCatalogoTrue();

    List<Producto> findBySectorAndVisibleEnCatalogoTrue(Producto.Sector sector);

    List<Producto> findByCategoriaIdOrderByNombre(Long categoriaId);

    List<Producto> findAllByOrderByNombre();

    // Control de referencias: una categoría con productos no se puede eliminar
    boolean existsByCategoriaId(Long categoriaId);

    // Alertas de stock mínimo: solo materiales (los equipos no se almacenan ni controlan stock)
    @org.springframework.data.jpa.repository.Query("""
            SELECT p FROM Producto p
            WHERE p.sector = com.bomerp.catalogo.producto.entity.Producto.Sector.MATERIAL_SERVICIO
              AND p.stockActual IS NOT NULL AND p.stockMinimo IS NOT NULL
              AND p.stockActual <= p.stockMinimo
            ORDER BY p.nombre
            """)
    List<Producto> findProductosEnAlerta();
}
