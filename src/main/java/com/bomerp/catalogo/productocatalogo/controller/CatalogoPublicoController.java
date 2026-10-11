package com.bomerp.catalogo.productocatalogo.controller;

import com.bomerp.catalogo.productocatalogo.dto.ProductoCatalogoDTO;
import com.bomerp.catalogo.producto.service.ProductoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Catálogo PÚBLICO (sin login), acordado con el cliente: solo visualización.
 * El DTO expone el precio de venta final; nunca el costo base ni el margen.
 */
@RestController
@RequestMapping("/api/v1/publico/catalogo")
public class CatalogoPublicoController {

    private final ProductoService productoService;

    public CatalogoPublicoController(ProductoService productoService) {
        this.productoService = productoService;
    }

    @GetMapping
    public List<ProductoCatalogoDTO> verCatalogo() {
        return productoService.listarCatalogoPublico();
    }
}
