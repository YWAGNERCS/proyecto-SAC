package com.jdrefrigeracion.compras.controllers;
import com.jdrefrigeracion.compras.services.CompraService;
import com.jdrefrigeracion.compras.dtos.CompraRequestDTO;
import com.jdrefrigeracion.compras.models.Compra;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/admin/compras")
public class CompraController {

    private final CompraService compraService;

    public CompraController(CompraService compraService) {
        this.compraService = compraService;
    }

    @GetMapping
    public List<Compra> listar() {
        return compraService.listarTodas();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Compra> buscarPorId(@PathVariable Long id) {
        return compraService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Compra> registrarCompra(@Valid @RequestBody CompraRequestDTO requestDTO) {
        Compra compraGuardada = compraService.registrarCompra(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(compraGuardada);
    }
}
