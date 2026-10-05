package com.bomerp.compras.compra.repository;

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
}
