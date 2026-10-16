package com.bomerp.compras.compra.service;
import java.time.LocalDate;
import com.bomerp.compras.detallecompra.entity.DetalleCompra;
import com.bomerp.compras.compra.dto.CompraRequestDTO;
import com.bomerp.compras.compra.entity.Compra;
import com.bomerp.compras.compra.repository.CompraRepository;

import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.catalogo.producto.service.ProductoService;
import com.bomerp.proveedores.proveedor.entity.Proveedor;
import com.bomerp.proveedores.proveedor.service.ProveedorService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class CompraService {

    private final CompraRepository compraRepository;
    private final ProveedorService proveedorService;
    private final ProductoService productoService;

    public CompraService(CompraRepository compraRepository, ProveedorService proveedorService, ProductoService productoService) {
        this.compraRepository = compraRepository;
        this.proveedorService = proveedorService;
        this.productoService = productoService;
    }

    public List<Compra> listarTodas() {
        return compraRepository.findAll();
    }

    public Optional<Compra> buscarPorId(Long id) {
        return compraRepository.findById(id);
    }

    /** Compras con filtros opcionales (fechas y proveedor), de la más reciente a la más antigua. */
    @Transactional(readOnly = true)
    public List<Compra> buscar(LocalDate desde, LocalDate hasta, Long proveedorId) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new IllegalArgumentException("La fecha 'desde' no puede ser posterior a 'hasta'.");
        }
        if (proveedorId != null) {
            proveedorService.obtenerPorId(proveedorId);   // 404 si el proveedor no existe
        }
        return compraRepository.buscar(desde, hasta, proveedorId);
    }

    /** Fachada para el kardex (Inventario): líneas compradas de un producto. */
    @Transactional(readOnly = true)
    public List<DetalleCompra> detallesDeProducto(Long productoId) {
        return compraRepository.detallesDeProducto(productoId);
    }

    /**
     * Registra una compra y ACTUALIZA EL STOCK de los productos comprados.
     */
    public Compra registrarCompra(CompraRequestDTO dto) {
        Proveedor proveedor = proveedorService.obtenerPorId(dto.proveedorId());

        Compra compra = new Compra();
        compra.setProveedor(proveedor);
        compra.setFechaCompra(dto.fechaCompra());
        compra.setNumeroFactura(dto.numeroFactura());
        compra.setMoneda(dto.moneda());
        compra.setTotal(dto.total());

        for (CompraRequestDTO.DetalleCompraRequestDTO detDto : dto.detalles()) {
            Producto producto = productoService.obtenerPorId(detDto.productoId());

            DetalleCompra detalle = new DetalleCompra();
            detalle.setProducto(producto);
            detalle.setCantidad(detDto.cantidad());
            detalle.setPrecioUnitarioCompra(detDto.precioUnitarioCompra());
            
            compra.addDetalle(detalle);

            // REGLA DE NEGOCIO: la compra sube el stock solo de los productos que lo controlan
            // (materiales). Los equipos no se almacenan, igual que en Ventas.
            if (producto.controlaStock()) {
                int stockActual = producto.getStockActual() == null ? 0 : producto.getStockActual();
                producto.setStockActual(stockActual + detDto.cantidad());
                productoService.guardar(producto);
            }
        }

        return compraRepository.save(compra);
    }
}
