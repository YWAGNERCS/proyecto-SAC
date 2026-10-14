package com.bomerp.ventas.venta.service;

import com.bomerp.ventas.venta.entity.Venta;
import com.bomerp.ventas.detalleventa.entity.DetalleVenta;
import com.bomerp.ventas.venta.repository.VentaRepository;

import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.catalogo.producto.service.ProductoService;
import com.bomerp.clientes.general.integration.Cliente;
import com.bomerp.clientes.cliente.service.ClienteService;
import com.bomerp.proformas.proforma.entity.Proforma;
import com.bomerp.proformas.proforma.service.ProformaService;
import com.bomerp.proformas.itemproforma.entity.ItemProforma;
import com.bomerp.config.RecursoNoEncontradoException;
import com.bomerp.config.ReglaNegocioException;
import com.bomerp.config.StockInsuficienteException;
import com.bomerp.postventa.mantenimiento.service.MantenimientoService;
import com.bomerp.facturacion.facturacion.service.FacturacionService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

@Service
@Transactional
@Slf4j
public class VentaService {

    private final VentaRepository ventaRepository;
    private final ProformaService proformaService;
    private final ClienteService clienteService;
    private final ProductoService productoService;
    private final MantenimientoService mantenimientoService;
    private final FacturacionService facturacionService;

    public VentaService(VentaRepository ventaRepository,
                        ProformaService proformaService,
                        ClienteService clienteService,
                        ProductoService productoService,
                        MantenimientoService mantenimientoService,
                        FacturacionService facturacionService) {
        this.ventaRepository = ventaRepository;
        this.proformaService = proformaService;
        this.clienteService = clienteService;
        this.productoService = productoService;
        this.mantenimientoService = mantenimientoService;
        this.facturacionService = facturacionService;
    }

    public List<Venta> listarTodas() {
        return ventaRepository.findAll();
    }

    /**
     * ⭐ RF principal confirmado con Lisandro:
     * "Convertir una proforma aceptada en venta, sin reescribir los datos"
     *
     * Toma una Proforma ya ACEPTADA y genera la Venta correspondiente,
     * copiando cliente, items y montos — el usuario no vuelve a digitar
     * nada de lo que ya estaba en la proforma.
     *
     * Reglas de negocio:
     *  - La proforma debe estar ACEPTADA (409 si no).
     *  - La proforma no debe haberse convertido ya en venta (409 si ya existe).
     *  - Para productos con controlaStock() = true, el stock debe ser suficiente (409 si no).
     */
    public Venta convertirProformaEnVenta(Long proformaId, Venta.TipoPago tipoPago, Integer diasCredito) {
        log.info("Iniciando transaccion: Convertir Proforma {} en Venta", proformaId);

        Proforma proforma = proformaService.obtenerPorId(proformaId);

        // ── Regla 1: Solo ACEPTADA se puede convertir ──────────────────────
        if (proforma.getEstado() != Proforma.EstadoProforma.ACEPTADA) {
            throw new ReglaNegocioException(
                    "Solo se puede convertir una proforma ACEPTADA. Estado actual: "
                            + proforma.getEstado());
        }

        // ── Regla 2: Evitar doble conversión ────────────────────────────────
        if (ventaRepository.existsByProformaOrigenId(proformaId)) {
            throw new ReglaNegocioException(
                    "La proforma #" + proformaId + " ya fue convertida en venta anteriormente.");
        }

        // ── Regla 3: Verificar stock suficiente (solo MATERIAL_SERVICIO) ────
        for (ItemProforma item : proforma.getItems()) {
            Producto producto = item.getProducto();
            if (producto.controlaStock()) {
                int stockDisponible = producto.getStockActual() != null ? producto.getStockActual() : 0;
                if (stockDisponible < item.getCantidad()) {
                    throw new StockInsuficienteException(
                            producto.getNombre(), stockDisponible, item.getCantidad());
                }
            }
        }

        Venta venta = new Venta();
        venta.setCliente(proforma.getCliente());
        venta.setProformaOrigen(proforma);
        venta.setFechaVenta(LocalDate.now());
        venta.setTipoPago(tipoPago);
        venta.setMoneda(Venta.Moneda.valueOf(proforma.getMoneda().name()));

        if (tipoPago == Venta.TipoPago.CREDITO) {
            int dias = diasCredito != null ? diasCredito : 30;
            venta.setDiasCredito(dias);
            venta.setFechaLimitePago(LocalDate.now().plusDays(dias));
        } else {
            venta.setEstadoPago(Venta.EstadoPago.PAGADO);
        }

        for (ItemProforma item : proforma.getItems()) {
            Producto producto = item.getProducto();

            // Descuento de inventario (solo para productos que controlan stock)
            if (producto.controlaStock()) {
                int nuevoStock = (producto.getStockActual() != null ? producto.getStockActual() : 0)
                        - item.getCantidad();
                producto.setStockActual(nuevoStock);
                productoService.guardar(producto);
            }

            venta.agregarDetalle(new DetalleVenta(
                    producto, item.getCantidad(), item.getPrecioUnitario()));
        }

        Venta ventaGuardada = ventaRepository.save(venta);
        mantenimientoService.programarParaVenta(ventaGuardada);

        emitirFactura(ventaGuardada);

        log.info("Transaccion exitosa: Venta registrada con ID {}", ventaGuardada.getId());
        return ventaGuardada;
    }

    /**
     * Venta directa, sin proforma previa
     * (RF: la empresa también vende sin cotizar antes).
     *
     * Reglas de negocio:
     *  - Para productos con controlaStock() = true, el stock debe ser suficiente (409 si no).
     */
    public Venta registrarVentaDirecta(Long clienteId, List<ItemVentaDTO> items,
                                       Venta.TipoPago tipoPago, Integer diasCredito) {
        log.info("Iniciando transaccion: Registrar Venta directa para Cliente ID {}", clienteId);

        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("La venta debe tener al menos un producto.");
        }
        for (ItemVentaDTO itemDTO : items) {
            if (itemDTO.productoId() == null || itemDTO.cantidad() == null || itemDTO.cantidad() <= 0) {
                throw new IllegalArgumentException("Cada línea debe tener un producto y una cantidad mayor que cero.");
            }
        }

        Cliente cliente = clienteService.obtenerPorId(clienteId);

        // ── Regla: Verificar stock suficiente antes de mutar nada ───────────
        for (ItemVentaDTO itemDTO : items) {
            Producto producto = productoService.obtenerPorId(itemDTO.productoId());
            if (producto.controlaStock()) {
                int stockDisponible = producto.getStockActual() != null ? producto.getStockActual() : 0;
                if (stockDisponible < itemDTO.cantidad()) {
                    throw new StockInsuficienteException(
                            producto.getNombre(), stockDisponible, itemDTO.cantidad());
                }
            }
        }

        Venta venta = new Venta();
        venta.setCliente(cliente);
        venta.setFechaVenta(LocalDate.now());
        venta.setTipoPago(tipoPago);

        if (tipoPago == Venta.TipoPago.CREDITO) {
            int dias = diasCredito != null ? diasCredito : 30;
            venta.setDiasCredito(dias);
            venta.setFechaLimitePago(LocalDate.now().plusDays(dias));
        } else {
            venta.setEstadoPago(Venta.EstadoPago.PAGADO);
        }

        Producto.Moneda monedaDetectada = null;
        for (ItemVentaDTO itemDTO : items) {
            Producto producto = productoService.obtenerPorId(itemDTO.productoId());
            monedaDetectada = producto.getMoneda();

            if (producto.controlaStock()) {
                int nuevoStock = (producto.getStockActual() != null ? producto.getStockActual() : 0)
                        - itemDTO.cantidad();
                producto.setStockActual(nuevoStock);
                productoService.guardar(producto);
            }

            venta.agregarDetalle(new DetalleVenta(
                    producto, itemDTO.cantidad(), producto.calcularPrecioVenta()));
        }
        venta.setMoneda(monedaDetectada != null
                ? Venta.Moneda.valueOf(monedaDetectada.name())
                : Venta.Moneda.SOLES);

        Venta ventaGuardada = ventaRepository.save(venta);
        mantenimientoService.programarParaVenta(ventaGuardada);

        emitirFactura(ventaGuardada);

        log.info("Transaccion exitosa: Venta registrada con ID {}", ventaGuardada.getId());
        return ventaGuardada;
    }

    /** RNF: seguimiento de cobranza — ventas a crédito vencidas */
    public List<Venta> listarVentasConPagoAtrasado() {
        return ventaRepository.findByEstadoPagoAndFechaLimitePagoBefore(
                Venta.EstadoPago.PENDIENTE, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public List<Venta> buscar(Venta.EstadoVenta estado, LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new IllegalArgumentException("La fecha 'desde' no puede ser posterior a 'hasta'.");
        }
        return ventaRepository.buscar(estado, desde, hasta);
    }

    public Venta marcarComoPagada(Long ventaId) {
        Venta venta = obtenerPorId(ventaId);
        if (venta.getEstado() == Venta.EstadoVenta.ANULADA) {
            throw new ReglaNegocioException("No se puede cobrar la venta #" + ventaId + ": está ANULADA.");
        }
        if (venta.getEstadoPago() == Venta.EstadoPago.PAGADO) {
            throw new ReglaNegocioException("La venta #" + ventaId + " ya está PAGADA.");
        }
        venta.setEstadoPago(Venta.EstadoPago.PAGADO);
        return ventaRepository.save(venta);
    }

    /**
     * Transición REGISTRADA -> ANULADA.
     *
     * Reglas de negocio:
     *  - Solo se anula una venta REGISTRADA (409 si ya está ANULADA): sin esta regla,
     *    anular dos veces devolvería el stock dos veces.
     *  - Efecto colateral: se devuelve al inventario lo vendido de los productos con
     *    controlaStock() = true (los equipos no se almacenan, no se restauran).
     */
    public Venta anular(Long ventaId) {
        Venta venta = obtenerPorId(ventaId);
        if (venta.getEstado() == Venta.EstadoVenta.ANULADA) {
            throw new ReglaNegocioException("La venta #" + ventaId + " ya está ANULADA.");
        }

        for (DetalleVenta detalle : venta.getDetalles()) {
            Producto producto = detalle.getProducto();
            if (producto.controlaStock()) {
                int stockActual = producto.getStockActual() != null ? producto.getStockActual() : 0;
                producto.setStockActual(stockActual + detalle.getCantidad());
                productoService.guardar(producto);
            }
        }
        venta.setEstado(Venta.EstadoVenta.ANULADA);

        log.info("Venta {} anulada: stock restaurado", ventaId);
        return ventaRepository.save(venta);
    }

    private Venta obtenerPorId(Long ventaId) {
        return ventaRepository.findById(ventaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Venta no encontrada: " + ventaId));
    }

    public record ItemVentaDTO(Long productoId, Integer cantidad) {}

    /** Emite y envía la factura electrónica; los errores no abortan la venta. */
    private void emitirFactura(Venta venta) {
        try {
            com.bomerp.facturacion.comprobante.entity.Comprobante comp =
                    facturacionService.emitirComprobanteParaVenta(venta.getId());
            facturacionService.enviarFacturaCorreo(comp.getId());
        } catch (Exception e) {
            log.error("Error al emitir/enviar factura para Venta {}: {}", venta.getId(), e.getMessage());
        }
    }
}
