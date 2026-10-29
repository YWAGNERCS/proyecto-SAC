package com.bomerp.clientes.sede.repository;

import com.bomerp.clientes.sede.entity.SedeCliente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SedeClienteRepository extends JpaRepository<SedeCliente, Long> {

    List<SedeCliente> findByCliente_IdOrderByNombre(Long clienteId);

    // Control de nombres repetidos en el mismo cliente (al editar se excluye la propia sede)
    boolean existsByCliente_IdAndNombreIgnoreCase(Long clienteId, String nombre);

    boolean existsByCliente_IdAndNombreIgnoreCaseAndIdNot(Long clienteId, String nombre, Long id);
}
