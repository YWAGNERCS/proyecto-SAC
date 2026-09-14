package com.jdrefrigeracion.catalogo.services;
import com.jdrefrigeracion.catalogo.repositories.ProductoRepository;
import com.jdrefrigeracion.catalogo.models.Producto;
import com.jdrefrigeracion.catalogo.dtos.ProductoCatalogoDTO;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductoService {

    private final ProductoRepository productoRepository;

    public ProductoService(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    /** Usado por el Portal Público (sin login) */
    public List<ProductoCatalogoDTO> listarCatalogoPublico() {
        return productoRepository.findByVisibleEnCatalogoTrue()
                .stream()
                .map(ProductoCatalogoDTO::desdeEntidad)
                .toList();
    }

    /** Usado por el Panel Administrador (con login) */
    public List<Producto> listarTodos() {
        return productoRepository.findAll();
    }

    public Producto guardar(Producto producto) {
        return productoRepository.save(producto);
    }

    /** Para la alerta de stock mínimo (RF02 / RF13 del brief) */
    public List<Producto> listarConStockBajo() {
        return productoRepository.findAll().stream()
                .filter(p -> p.getStockActual() != null
                        && p.getStockMinimo() != null
                        && p.getStockActual() <= p.getStockMinimo())
                .toList();
    }
}
