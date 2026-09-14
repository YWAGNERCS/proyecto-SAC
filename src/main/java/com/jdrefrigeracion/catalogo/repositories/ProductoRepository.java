package com.jdrefrigeracion.catalogo.repositories;
import com.jdrefrigeracion.catalogo.models.Producto;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    // Solo lo que debe verse en el catálogo público
    List<Producto> findByVisibleEnCatalogoTrue();

    List<Producto> findBySectorAndVisibleEnCatalogoTrue(Producto.Sector sector);

    // Para las alertas de stock mínimo (materiales de servicio)
    @org.springframework.data.jpa.repository.Query("SELECT p FROM Producto p WHERE p.stockActual <= p.stockMinimo")
    List<Producto> findProductosEnAlerta();
}
