package com.bomerp.catalogo.producto.service;
import com.bomerp.catalogo.producto.repository.ProductoRepository;
import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.catalogo.productocatalogo.dto.ProductoCatalogoDTO;
import com.bomerp.catalogo.categoria.entity.Categoria;
import com.bomerp.catalogo.categoria.service.CategoriaService;
import com.bomerp.catalogo.producto.dto.ProductoRequestDTO;
import com.bomerp.config.RecursoNoEncontradoException;
import com.bomerp.config.ReglaNegocioException;
import com.bomerp.config.StockInsuficienteException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

@Service
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final CategoriaService categoriaService;
    private final EntityManager entityManager;

    public ProductoService(ProductoRepository productoRepository, CategoriaService categoriaService,
                           EntityManager entityManager) {
        this.productoRepository = productoRepository;
        this.categoriaService = categoriaService;
        this.entityManager = entityManager;
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

    /**
     * Edita los datos del producto. El stock NO se cambia aquí: solo se mueve con compras, ventas,
     * órdenes de trabajo y ajustes de inventario (así todo queda en el kardex y no se pisa una venta
     * que se esté registrando al mismo tiempo).
     */
    @Transactional
    public Producto actualizar(Long id, ProductoRequestDTO dto) {
        Producto producto = obtenerPorId(id);
        Integer stock = producto.getStockActual();
        aplicar(dto, producto);
        producto.setStockActual(stock);
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

    /** Fachada pública para otros módulos: evita que accedan a ProductoRepository. */
    public Producto obtenerPorId(Long id) {
        return productoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Producto no encontrado con ID: " + id));
    }

    // ── Movimientos de stock (únicos puntos que cambian stockActual) ───────

    /** Cuánto espera una operación a que otra suelte el producto antes de responder 409 (ms). */
    private static final String ESPERA_BLOQUEO = "jakarta.persistence.lock.timeout";
    private static final int MILIS_ESPERA = 10_000;

    /**
     * Bloquea las filas de los productos (SELECT ... FOR UPDATE) y relee su stock, hasta que termine la
     * transacción: otra venta, compra u orden que toque esos productos espera su turno. Se bloquean en
     * orden de id para que dos operaciones con los mismos productos no se esperen mutuamente.
     */
    @Transactional
    public Map<Long, Producto> bloquearParaStock(Collection<Long> ids) {
        Map<Long, Producto> bloqueados = new LinkedHashMap<>();
        for (Long id : new TreeSet<>(ids)) {
            Producto p = entityManager.find(Producto.class, id);
            if (p == null) {
                throw new RecursoNoEncontradoException("Producto no encontrado con ID: " + id);
            }
            // refresh con bloqueo: relee el stock de la base aunque el producto ya estuviera cargado
            entityManager.refresh(p, LockModeType.PESSIMISTIC_WRITE, Map.of(ESPERA_BLOQUEO, MILIS_ESPERA));
            bloqueados.put(id, p);
        }
        return bloqueados;
    }

    /**
     * Descuenta stock de varios productos a la vez (venta, conversión de proforma, orden de trabajo).
     * Suma las cantidades del mismo producto repetido en varias líneas, valida todo con el stock leído
     * bajo bloqueo y recién entonces descuenta: o se descuenta todo o nada (409 si alguno no alcanza).
     * Los productos que no controlan stock (equipos, servicios) se ignoran.
     *
     * @param cantidades producto → unidades (pueden venir repetidos: se suman antes de llamar)
     */
    @Transactional
    public void descontarStock(Map<Long, Integer> cantidades) {
        Map<Long, Producto> productos = bloquearParaStock(cantidades.keySet());
        for (Map.Entry<Long, Integer> e : cantidades.entrySet()) {
            Producto p = productos.get(e.getKey());
            if (!p.controlaStock()) continue;
            int disponible = p.getStockActual() != null ? p.getStockActual() : 0;
            if (disponible < e.getValue()) {
                throw new StockInsuficienteException(p.getNombre(), disponible, e.getValue());
            }
        }
        for (Map.Entry<Long, Integer> e : cantidades.entrySet()) {
            Producto p = productos.get(e.getKey());
            if (p.controlaStock()) {
                p.setStockActual(p.getStockActual() - e.getValue());
            }
        }
    }

    /** Suma stock (compra, anulación de venta) con el mismo bloqueo. Ignora lo que no controla stock. */
    @Transactional
    public void sumarStock(Map<Long, Integer> cantidades) {
        Map<Long, Producto> productos = bloquearParaStock(cantidades.keySet());
        for (Map.Entry<Long, Integer> e : cantidades.entrySet()) {
            Producto p = productos.get(e.getKey());
            if (p.controlaStock()) {
                p.setStockActual((p.getStockActual() != null ? p.getStockActual() : 0) + e.getValue());
            }
        }
    }

    /** Materiales cuyo stock actual llegó o bajó del stock mínimo (los equipos no controlan stock). */
    public List<Producto> listarEnAlerta() {
        return productoRepository.findProductosEnAlerta();
    }
}
