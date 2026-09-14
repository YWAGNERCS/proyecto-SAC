package com.jdrefrigeracion.clientes.controllers;
import com.jdrefrigeracion.clientes.integrations.Cliente;
import com.jdrefrigeracion.clientes.services.ClienteService;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints del PANEL ADMINISTRADOR — en el proyecto final, esta ruta
 * ("/api/admin/...") quedará protegida con Spring Security, exigiendo
 * login (Lisandro o Lucy). Por ahora, mientras se construye el resto
 * del sistema, queda abierta para poder probarla desde Postman/Insomnia.
 */
@RestController
@RequestMapping("/api/admin/clientes")
public class ClienteController {

    private final ClienteService clienteService;

    public ClienteController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @GetMapping
    public List<Cliente> listar() {
        return clienteService.listarTodos();
    }

    @PostMapping
    public Cliente crear(@Valid @RequestBody Cliente cliente) {
        return clienteService.guardar(cliente);
    }

    @GetMapping("/autocompletar/ruc/{ruc}")
    public Cliente autocompletarPorRuc(@PathVariable String ruc) {
        return clienteService.autocompletarPorRuc(ruc);
    }

    @GetMapping("/autocompletar/dni/{dni}")
    public Cliente autocompletarPorDni(@PathVariable String dni) {
        return clienteService.autocompletarPorDni(dni);
    }
}
