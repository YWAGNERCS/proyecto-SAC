package com.bomerp.compras.compra.service;
import java.time.LocalDate;
import com.bomerp.config.ReglaNegocioException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    /** Serie de 4 caracteres (F001, E001, FA01…) + guion + número de hasta 8 dígitos. */
    private static final Pattern FACTURA = Pattern.compile("^([A-Z0-9]{4})-(\\d{1,8})$");

    /**
     * Normaliza el N° de factura del proveedor: mayúsculas, sin espacios y número a 8 dígitos
     * (F001-123 → F001-00000123), para detectar la misma factura escrita de dos formas.
     */
    static String normalizarFactura(String numero) {
        String limpio = numero == null ? "" : numero.trim().toUpperCase().replace(" ", "");
        Matcher m = FACTURA.matcher(limpio);
        if (!m.matches()) {
            throw new IllegalArgumentException(
                    "El N° de factura debe tener el formato SERIE-NÚMERO, por ejemplo F001-123.");
        }
        return m.group(1) + "-" + String.format("%08d", Long.parseLong(m.group(2)));
    }

    /**
     * Registra una compra y ACTUALIZA EL STOCK de los productos comprados.
     *
     * Reglas: cantidades ≥ 1 y precios > 0 (validados en el DTO), N° de factura con formato
     * SERIE-NÚMERO, la misma factura del mismo proveedor no se registra dos veces (409)
     * y el total lo calcula el servidor con las líneas.
     */
    public Compra registrarCompra(CompraRequestDTO dto) {
        Proveedor proveedor = proveedorService.obtenerPorId(dto.proveedorId());
        String numeroFactura = normalizarFactura(dto.numeroFactura());
        compraRepository.findFirstByProveedor_IdAndNumeroFactura(proveedor.getId(), numeroFactura)
                .ifPresent(existente -> {
                    throw new ReglaNegocioException("La factura " + numeroFactura + " de "
                            + proveedor.getNombreORazonSocial() + " ya está registrada (compra #"
                            + existente.getId() + "). No se puede ingresar dos veces.");
                });

        Compra compra = new Compra();
        compra.setProveedor(proveedor);
        compra.setFechaCompra(dto.fechaCompra());
        compra.setNumeroFactura(numeroFactura);
        compra.setMoneda(dto.moneda());
        BigDecimal total = BigDecimal.ZERO;

        for (CompraRequestDTO.DetalleCompraRequestDTO detDto : dto.detalles()) {
            Producto producto = productoService.obtenerPorId(detDto.productoId());

            DetalleCompra detalle = new DetalleCompra();
            detalle.setProducto(producto);
            detalle.setCantidad(detDto.cantidad());
            detalle.setPrecioUnitarioCompra(detDto.precioUnitarioCompra().setScale(2, RoundingMode.HALF_UP));
            total = total.add(detalle.getPrecioUnitarioCompra().multiply(BigDecimal.valueOf(detDto.cantidad())));

            compra.addDetalle(detalle);

            // REGLA DE NEGOCIO: la compra sube el stock solo de los productos que lo controlan
            // (materiales). Los equipos no se almacenan, igual que en Ventas.
            if (producto.controlaStock()) {
                int stockActual = producto.getStockActual() == null ? 0 : producto.getStockActual();
                producto.setStockActual(stockActual + detDto.cantidad());
                productoService.guardar(producto);
            }
        }

        compra.setTotal(total.setScale(2, RoundingMode.HALF_UP));
        return compraRepository.save(compra);
    }
}
