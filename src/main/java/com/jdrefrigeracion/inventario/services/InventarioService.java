package com.jdrefrigeracion.inventario.services;

import com.jdrefrigeracion.catalogo.models.Producto;
import com.jdrefrigeracion.catalogo.repositories.ProductoRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InventarioService {

    private final ProductoRepository productoRepository;

    public InventarioService(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    /**
     * Obtiene los productos cuyo stock actual sea menor o igual al stock mínimo.
     * @return Lista de productos en estado crítico de inventario.
     */
    public List<Producto> obtenerProductosEnAlerta() {
        return productoRepository.findProductosEnAlerta();
    }
}
