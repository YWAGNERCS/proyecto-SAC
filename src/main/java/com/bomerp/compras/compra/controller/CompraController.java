package com.bomerp.compras.compra.controller;

import com.bomerp.compras.compra.service.CompraService;
import com.bomerp.compras.compra.dto.CompraRequestDTO;
import com.bomerp.compras.compra.dto.CompraResponseDTO;
import com.bomerp.compras.compra.entity.Compra;

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
    public List<CompraResponseDTO> listar() {
        return compraService.listarTodas().stream()
                .map(CompraResponseDTO::desdeEntidad)
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<CompraResponseDTO> buscarPorId(@PathVariable Long id) {
        return compraService.buscarPorId(id)
                .map(CompraResponseDTO::desdeEntidad)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<CompraResponseDTO> registrarCompra(@Valid @RequestBody CompraRequestDTO requestDTO) {
        Compra compraGuardada = compraService.registrarCompra(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(CompraResponseDTO.desdeEntidad(compraGuardada));
    }
}
