package com.jdrefrigeracion.clientes.repositories;
import com.jdrefrigeracion.clientes.integrations.Cliente;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    Optional<Cliente> findByRuc(String ruc);
    Optional<Cliente> findByDni(String dni);
}
