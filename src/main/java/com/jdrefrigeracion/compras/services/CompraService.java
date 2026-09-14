package com.jdrefrigeracion.compras.services;
import com.jdrefrigeracion.compras.models.DetalleCompra;
import com.jdrefrigeracion.compras.dtos.CompraRequestDTO;
import com.jdrefrigeracion.compras.models.Compra;
import com.jdrefrigeracion.compras.repositories.CompraRepository;

import com.jdrefrigeracion.catalogo.models.Producto;
import com.jdrefrigeracion.catalogo.repositories.ProductoRepository;
import com.jdrefrigeracion.proveedores.models.Proveedor;
import com.jdrefrigeracion.proveedores.repositories.ProveedorRepository;
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
