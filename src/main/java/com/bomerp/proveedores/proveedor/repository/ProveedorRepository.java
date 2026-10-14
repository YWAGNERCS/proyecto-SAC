package com.bomerp.proveedores.proveedor.repository;
import com.bomerp.proveedores.proveedor.entity.Proveedor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProveedorRepository extends JpaRepository<Proveedor, Long> {
    Optional<Proveedor> findByRuc(String ruc);

    // Control de duplicados (al editar se excluye el propio registro)
    boolean existsByRuc(String ruc);
    boolean existsByRucAndIdNot(String ruc, Long id);
    boolean existsByDni(String dni);
    boolean existsByDniAndIdNot(String dni, Long id);
}
