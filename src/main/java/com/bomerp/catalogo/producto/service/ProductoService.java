package com.bomerp.catalogo.producto.service;
import com.bomerp.catalogo.producto.repository.ProductoRepository;
import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.catalogo.productocatalogo.dto.ProductoCatalogoDTO;
import com.bomerp.config.RecursoNoEncontradoException;

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

    /** Fachada pública para otros módulos: evita que accedan a ProductoRepository. */
    public Producto obtenerPorId(Long id) {
        return productoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Producto no encontrado con ID: " + id));
    }

    /** Productos cuyo stock actual llegó o bajó del stock mínimo. */
    public List<Producto> listarEnAlerta() {
        return productoRepository.findProductosEnAlerta();
    }
}
