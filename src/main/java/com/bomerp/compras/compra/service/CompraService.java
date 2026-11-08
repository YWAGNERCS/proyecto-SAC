package com.bomerp.compras.compra.service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

import com.bomerp.config.paginacion.Orden;
import com.bomerp.config.tipocambio.TipoCambioService;
import org.springframework.data.domain.PageImpl;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional
public class CompraService {

    private final CompraRepository compraRepository;
    private final ProveedorService proveedorService;
    private final ProductoService productoService;
    private final TipoCambioService tipoCambioService;

    public CompraService(CompraRepository compraRepository, ProveedorService proveedorService, ProductoService productoService,
                         TipoCambioService tipoCambioService) {
        this.compraRepository = compraRepository;
        this.proveedorService = proveedorService;
        this.productoService = productoService;
        this.tipoCambioService = tipoCambioService;
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

    /** Orden de siempre: la más reciente primero. */
    private static final Comparator<Compra> RECIENTES_PRIMERO =
            Comparator.comparing(Compra::getFechaCompra).thenComparing(Compra::getId).reversed();

    /**
     * Columnas que la pantalla de Compras puede ordenar. El total se compara en soles: la compra no guarda
     * tipo de cambio, así que las compras en dólares se llevan a soles con el de hoy.
     */
    private static Map<String, Orden.Campo<Compra>> camposOrden(BigDecimal tipoCambioHoy) {
        return Map.of(
                "id", Orden.valor(Compra::getId),
                "proveedor", Orden.texto((Compra c) -> c.getProveedor() != null ? c.getProveedor().getNombreORazonSocial() : null),
                "fecha", Orden.valor(Compra::getFechaCompra),
                "factura", Orden.texto(Compra::getNumeroFactura),
                "total", Orden.valor((Compra c) -> Orden.enSoles(c.getTotal(), c.getMoneda() == Producto.Moneda.DOLARES,
                        null, tipoCambioHoy)));
    }

    /**
     * Compras por páginas, con los mismos filtros que buscar(). Sin orden pedido pagina la base de datos;
     * con un orden ("proveedor,asc", "total,desc"...) se ordena todo el filtro y luego se toma la página.
     */
    @Transactional(readOnly = true)
    public Page<Compra> buscarPagina(LocalDate desde, LocalDate hasta, Long proveedorId, Pageable pedido, String orden) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new IllegalArgumentException("La fecha 'desde' no puede ser posterior a 'hasta'.");
        }
        if (orden == null || orden.isBlank()) {
            return compraRepository.buscarPagina(desde, hasta, proveedorId, pedido);
        }
        BigDecimal tipoCambioHoy = Orden.pide(orden, "total") ? tipoCambioService.resolverSiSePuede(null) : null;
        List<Compra> ordenadas = Orden.ordenar(compraRepository.buscar(desde, hasta, proveedorId), orden,
                camposOrden(tipoCambioHoy), RECIENTES_PRIMERO);
        int inicio = (int) Math.min(pedido.getOffset(), ordenadas.size());
        int fin = Math.min(inicio + pedido.getPageSize(), ordenadas.size());
        return new PageImpl<>(ordenadas.subList(inicio, fin), pedido, ordenadas.size());
    }

    /** Fachada para el kardex (Inventario): líneas compradas de un producto. */
    @Transactional(readOnly = true)
    public List<DetalleCompra> detallesDeProducto(Long productoId) {
        return compraRepository.detallesDeProducto(productoId);
    }

    /**
     * Contado: PAGADA. Crédito: PENDIENTE, vence fecha de compra + días (1 a 365); queda en Cuentas por pagar.
     */
    private static void aplicarFormaPago(Compra compra, Compra.TipoPago tipoPago, Integer diasCredito) {
        Compra.TipoPago tipo = tipoPago == null ? Compra.TipoPago.CONTADO : tipoPago;
        compra.setTipoPago(tipo);
        if (tipo == Compra.TipoPago.CREDITO) {
            if (diasCredito == null || diasCredito < 1 || diasCredito > 365) {
                throw new IllegalArgumentException("Una compra a crédito necesita los días de crédito (1 a 365).");
            }
            compra.setDiasCredito(diasCredito);
            compra.setFechaVencimiento(compra.getFechaCompra().plusDays(diasCredito));
            compra.setEstadoPago(Compra.EstadoPago.PENDIENTE);
        } else {
            compra.setDiasCredito(null);
            compra.setFechaVencimiento(null);
            compra.setEstadoPago(Compra.EstadoPago.PAGADA);
        }
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
        return registrarCompra(dto, null);
    }

    /** @param usuario preferred_username de quien la registra */
    public Compra registrarCompra(CompraRequestDTO dto, String usuario) {
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
        compra.setRegistradoPor(usuario);
        aplicarFormaPago(compra, dto.tipoPago(), dto.diasCredito());
        BigDecimal total = BigDecimal.ZERO;
        java.util.Map<Long, Integer> entradas = new java.util.HashMap<>();

        for (CompraRequestDTO.DetalleCompraRequestDTO detDto : dto.detalles()) {
            Producto producto = productoService.obtenerPorId(detDto.productoId());

            DetalleCompra detalle = new DetalleCompra();
            detalle.setProducto(producto);
            detalle.setCantidad(detDto.cantidad());
            detalle.setPrecioUnitarioCompra(detDto.precioUnitarioCompra().setScale(2, RoundingMode.HALF_UP));
            total = total.add(detalle.getPrecioUnitarioCompra().multiply(BigDecimal.valueOf(detDto.cantidad())));

            compra.addDetalle(detalle);

            entradas.merge(producto.getId(), detDto.cantidad(), Integer::sum);
        }

        // La compra sube el stock solo de los materiales (los equipos no se almacenan), con bloqueo
        productoService.sumarStock(entradas);
        // Después del bloqueo (refresca los productos desde la base): el último costo de cada uno
        for (DetalleCompra detalle : compra.getDetalles()) {
            registrarUltimoCosto(detalle.getProducto(), detalle.getPrecioUnitarioCompra(), dto.moneda(), dto.fechaCompra());
        }
        compra.fijarTotalConIgv(total);
        return compraRepository.save(compra);
    }

    /**
     * Auditoría M1: el producto guarda el costo de esta compra sin IGV, en su propia moneda (si la compra es en
     * otra, con el tipo de cambio SUNAT de la fecha de compra; sin tipo de cambio no se actualiza). El precio de
     * venta no cambia solo: en Ventas sale un aviso y se puede editar el precio de la línea.
     */
    private void registrarUltimoCosto(Producto producto, BigDecimal precioConIgv, Producto.Moneda monedaCompra,
                                      LocalDate fechaCompra) {
        BigDecimal costo = Compra.sinIgv(precioConIgv);
        if (producto.getMoneda() != monedaCompra) {
            BigDecimal tc = tipoCambioService.resolverSiSePuede(null, fechaCompra);
            if (tc == null) return;
            costo = TipoCambioService.convertir(costo, monedaCompra.name(), producto.getMoneda().name(), tc);
        }
        producto.registrarCostoDeCompra(costo, fechaCompra);
    }
}
