package com.jdrefrigeracion.compras.repositories;
import com.jdrefrigeracion.compras.models.Compra;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CompraRepository extends JpaRepository<Compra, Long> {
}
