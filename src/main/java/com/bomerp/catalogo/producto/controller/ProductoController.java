package com.bomerp.catalogo.producto.controller;

import com.bomerp.catalogo.producto.dto.ProductoRequestDTO;
import com.bomerp.catalogo.producto.dto.ProductoResponseDTO;
import com.bomerp.catalogo.producto.service.ProductoService;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/productos")
@Slf4j
public class ProductoController {

    private final ProductoService productoService;

    public ProductoController(ProductoService productoService) {
        this.productoService = productoService;
    }

    /** Lista productos; con ?categoriaId= filtra por categoría (404 si la categoría no existe). */
    @GetMapping
    public List<ProductoResponseDTO> listar(@RequestParam(required = false) Long categoriaId) {
        return productoService.listar(categoriaId).stream().map(ProductoResponseDTO::desdeEntidad).toList();
    }

    @GetMapping("/{id}")
    public ProductoResponseDTO obtener(@PathVariable Long id) {
        return ProductoResponseDTO.desdeEntidad(productoService.obtenerPorId(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductoResponseDTO crear(@Valid @RequestBody ProductoRequestDTO request) {
        log.info("Creando producto '{}' en la categoría {}", request.nombre(), request.categoriaId());
        return ProductoResponseDTO.desdeEntidad(productoService.crear(request));
    }

    @PutMapping("/{id}")
    public ProductoResponseDTO actualizar(@PathVariable Long id, @Valid @RequestBody ProductoRequestDTO request) {
        log.info("Actualizando producto {}", id);
        return ProductoResponseDTO.desdeEntidad(productoService.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id) {
        log.info("Eliminando producto {}", id);
        productoService.eliminar(id);
    }
}
