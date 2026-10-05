package com.bomerp.compras.compra.service;
import com.bomerp.compras.detallecompra.entity.DetalleCompra;
import com.bomerp.compras.compra.dto.CompraRequestDTO;
import com.bomerp.compras.compra.entity.Compra;
import com.bomerp.compras.compra.repository.CompraRepository;

import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.catalogo.producto.repository.ProductoRepository;
import com.bomerp.proveedores.proveedor.entity.Proveedor;
import com.bomerp.proveedores.proveedor.repository.ProveedorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class CompraService {

    private final CompraRepository compraRepository;
    private final ProveedorRepository proveedorRepository;
    private final ProductoRepository productoRepository;

    public CompraService(CompraRepository compraRepository, ProveedorRepository proveedorRepository, ProductoRepository productoRepository) {
        this.compraRepository = compraRepository;
        this.proveedorRepository = proveedorRepository;
        this.productoRepository = productoRepository;
    }

    public List<Compra> listarTodas() {
        return compraRepository.findAll();
    }

    public Optional<Compra> buscarPorId(Long id) {
        return compraRepository.findById(id);
    }

    /**
     * Registra una compra y ACTUALIZA EL STOCK de los productos comprados.
     */
    public Compra registrarCompra(CompraRequestDTO dto) {
        Proveedor proveedor = proveedorRepository.findById(dto.proveedorId())
                .orElseThrow(() -> new IllegalArgumentException("Proveedor no encontrado con ID: " + dto.proveedorId()));

        Compra compra = new Compra();
        compra.setProveedor(proveedor);
        compra.setFechaCompra(dto.fechaCompra());
        compra.setNumeroFactura(dto.numeroFactura());
        compra.setMoneda(dto.moneda());
        compra.setTotal(dto.total());

        for (CompraRequestDTO.DetalleCompraRequestDTO detDto : dto.detalles()) {
            Producto producto = productoRepository.findById(detDto.productoId())
                    .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado con ID: " + detDto.productoId()));

            DetalleCompra detalle = new DetalleCompra();
            detalle.setProducto(producto);
            detalle.setCantidad(detDto.cantidad());
            detalle.setPrecioUnitarioCompra(detDto.precioUnitarioCompra());
            
            compra.addDetalle(detalle);

            // REGLA DE NEGOCIO AUTÓNOMA: Incrementar el stock del producto
            int stockActual = producto.getStockActual() == null ? 0 : producto.getStockActual();
            producto.setStockActual(stockActual + detDto.cantidad());
            
            // Guardamos la actualización del stock
            productoRepository.save(producto);
        }

        return compraRepository.save(compra);
    }
}
