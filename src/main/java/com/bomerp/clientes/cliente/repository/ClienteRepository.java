package com.bomerp.clientes.cliente.repository;
import com.bomerp.clientes.general.integration.Cliente;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    Optional<Cliente> findFirstByRuc(String ruc);
    Optional<Cliente> findFirstByDni(String dni);
}
