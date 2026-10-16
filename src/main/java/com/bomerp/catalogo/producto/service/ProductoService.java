package com.bomerp.catalogo.producto.service;
import com.bomerp.catalogo.producto.repository.ProductoRepository;
import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.catalogo.productocatalogo.dto.ProductoCatalogoDTO;
import com.bomerp.catalogo.categoria.entity.Categoria;
import com.bomerp.catalogo.categoria.service.CategoriaService;
import com.bomerp.catalogo.producto.dto.ProductoRequestDTO;
import com.bomerp.config.RecursoNoEncontradoException;
import com.bomerp.config.ReglaNegocioException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final CategoriaService categoriaService;

    public ProductoService(ProductoRepository productoRepository, CategoriaService categoriaService) {
        this.productoRepository = productoRepository;
        this.categoriaService = categoriaService;
    }

    // ── CRUD del panel administrativo (S2) con su categoría (S3) ──────────

    /** Lista todos los productos, o solo los de una categoría si se indica (404 si la categoría no existe). */
    @Transactional(readOnly = true)
    public List<Producto> listar(Long categoriaId) {
        if (categoriaId == null) {
            return productoRepository.findAllByOrderByNombre();
        }
        categoriaService.obtenerPorId(categoriaId);
        return productoRepository.findByCategoriaIdOrderByNombre(categoriaId);
    }

    @Transactional
    public Producto crear(ProductoRequestDTO dto) {
        Producto producto = new Producto();
        aplicar(dto, producto);
        return productoRepository.save(producto);
    }

    @Transactional
    public Producto actualizar(Long id, ProductoRequestDTO dto) {
        Producto producto = obtenerPorId(id);
        aplicar(dto, producto);
        return productoRepository.save(producto);
    }

    /** 409 si el producto ya figura en ventas, proformas o compras: se conserva el historial. */
    @Transactional
    public void eliminar(Long id) {
        Producto producto = obtenerPorId(id);
        try {
            productoRepository.delete(producto);
            productoRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ReglaNegocioException("No se puede eliminar '" + producto.getNombre()
                    + "': ya figura en ventas, proformas o compras. Puede ocultarlo del catálogo.");
        }
    }

    private void aplicar(ProductoRequestDTO dto, Producto producto) {
        Categoria categoria = categoriaService.obtenerPorId(dto.categoriaId());   // 404 si no existe
        producto.setNombre(dto.nombre().trim());
        producto.setDescripcion(dto.descripcion());
        producto.setCategoria(categoria);
        producto.setSector(dto.sector());
        producto.setCostoBase(dto.costoBase());
        producto.setMargenPorcentaje(dto.margenPorcentaje());
        producto.setMoneda(dto.moneda());
        producto.setStockActual(dto.stockActual() != null ? dto.stockActual() : 0);
        producto.setStockMinimo(dto.stockMinimo() != null ? dto.stockMinimo() : 0);
        producto.setVisibleEnCatalogo(dto.visibleEnCatalogo() == null || dto.visibleEnCatalogo());
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

    /** Para la alerta de stock mínimo (RF02 / RF13 del brief): solo materiales que controlan stock. */
    public List<Producto> listarConStockBajo() {
        return listarEnAlerta();
    }

    /** Fachada pública para otros módulos: evita que accedan a ProductoRepository. */
    public Producto obtenerPorId(Long id) {
        return productoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Producto no encontrado con ID: " + id));
    }

    /** Materiales cuyo stock actual llegó o bajó del stock mínimo (los equipos no controlan stock). */
    public List<Producto> listarEnAlerta() {
        return productoRepository.findProductosEnAlerta();
    }
}
