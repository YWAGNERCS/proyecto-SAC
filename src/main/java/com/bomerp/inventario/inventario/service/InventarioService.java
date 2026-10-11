package com.bomerp.inventario.inventario.service;

import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.catalogo.producto.service.ProductoService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InventarioService {

    private final ProductoService productoService;

    public InventarioService(ProductoService productoService) {
        this.productoService = productoService;
    }

    /**
     * Obtiene los productos cuyo stock actual sea menor o igual al stock mínimo.
     * @return Lista de productos en estado crítico de inventario.
     */
    public List<Producto> obtenerProductosEnAlerta() {
        return productoService.listarEnAlerta();
    }
}
