package com.bomerp.clientes.cliente.controller;
import com.bomerp.clientes.general.integration.Cliente;
import com.bomerp.clientes.cliente.service.ClienteService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints del PANEL ADMINISTRADOR — en el proyecto final, esta ruta
 * ("/api/v1/admin/...") quedará protegida con Spring Security, exigiendo
 * login (Lisandro o Lucy). Por ahora, mientras se construye el resto
 * del sistema, queda abierta para poder probarla desde Postman/Insomnia.
 */
@RestController
@RequestMapping("/api/v1/admin/clientes")
public class ClienteController {

    private final ClienteService clienteService;

    public ClienteController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @GetMapping
    public List<Cliente> listar() {
        return clienteService.listarTodos();
    }

    @GetMapping("/{id}")
    public Cliente obtener(@PathVariable Long id) {
        return clienteService.obtenerPorId(id);   // 404 si no existe
    }

    /** Valida RUC/DNI según el tipo, duplicados (409) y correo. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Cliente crear(@Valid @RequestBody Cliente cliente) {
        return clienteService.guardar(cliente);
    }

    @PutMapping("/{id}")
    public Cliente actualizar(@PathVariable Long id, @Valid @RequestBody Cliente cliente) {
        return clienteService.actualizar(id, cliente);
    }

    /** 409 si el cliente ya tiene proformas o ventas. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id) {
        clienteService.eliminar(id);
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
