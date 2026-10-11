package com.bomerp.ventas.venta.service;

import com.bomerp.ventas.venta.entity.Venta;
import com.bomerp.ventas.detalleventa.entity.DetalleVenta;
import com.bomerp.ventas.venta.repository.VentaRepository;

import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.catalogo.producto.service.ProductoService;
import com.bomerp.clientes.general.integration.Cliente;
import com.bomerp.clientes.cliente.service.ClienteService;
import com.bomerp.cotizaciones.cotizacion.entity.Cotizacion;
import com.bomerp.cotizaciones.cotizacion.service.CotizacionService;
import com.bomerp.cotizaciones.itemcotizacion.entity.ItemCotizacion;
import com.bomerp.config.RecursoNoEncontradoException;
import com.bomerp.config.ReglaNegocioException;
import com.bomerp.config.StockInsuficienteException;
import com.bomerp.mantenimiento.mantenimiento.service.MantenimientoService;
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
    private final CotizacionService cotizacionService;
    private final ClienteService clienteService;
    private final ProductoService productoService;
    private final MantenimientoService mantenimientoService;
    private final FacturacionService facturacionService;

    public VentaService(VentaRepository ventaRepository,
                        CotizacionService cotizacionService,
                        ClienteService clienteService,
                        ProductoService productoService,
                        MantenimientoService mantenimientoService,
                        FacturacionService facturacionService) {
        this.ventaRepository = ventaRepository;
        this.cotizacionService = cotizacionService;
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
     * "Convertir una cotización aceptada en venta, sin reescribir los datos"
     *
     * Toma una Cotizacion ya ACEPTADA y genera la Venta correspondiente,
     * copiando cliente, items y montos — el usuario no vuelve a digitar
     * nada de lo que ya estaba en la cotización.
     *
     * Reglas de negocio:
     *  - La cotización debe estar ACEPTADA (409 si no).
     *  - La cotización no debe haberse convertido ya en venta (409 si ya existe).
     *  - Para productos con controlaStock() = true, el stock debe ser suficiente (409 si no).
     */
    public Venta convertirCotizacionEnVenta(Long cotizacionId, Venta.TipoPago tipoPago, Integer diasCredito) {
        log.info("Iniciando transaccion: Convertir Cotizacion {} en Venta", cotizacionId);

        Cotizacion cotizacion = cotizacionService.obtenerPorId(cotizacionId);

        // ── Regla 1: Solo ACEPTADA se puede convertir ──────────────────────
        if (cotizacion.getEstado() != Cotizacion.EstadoCotizacion.ACEPTADA) {
            throw new ReglaNegocioException(
                    "Solo se puede convertir una cotización ACEPTADA. Estado actual: "
                            + cotizacion.getEstado());
        }

        // ── Regla 2: Evitar doble conversión ────────────────────────────────
        if (ventaRepository.existsByCotizacionOrigenId(cotizacionId)) {
            throw new ReglaNegocioException(
                    "La cotización #" + cotizacionId + " ya fue convertida en venta anteriormente.");
        }

        // ── Regla 3: Verificar stock suficiente (solo MATERIAL_SERVICIO) ────
        for (ItemCotizacion item : cotizacion.getItems()) {
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
        venta.setCliente(cotizacion.getCliente());
        venta.setCotizacionOrigen(cotizacion);
        venta.setFechaVenta(LocalDate.now());
        venta.setTipoPago(tipoPago);
        venta.setMoneda(Venta.Moneda.valueOf(cotizacion.getMoneda().name()));

        if (tipoPago == Venta.TipoPago.CREDITO) {
            int dias = diasCredito != null ? diasCredito : 30;
            venta.setDiasCredito(dias);
            venta.setFechaLimitePago(LocalDate.now().plusDays(dias));
        } else {
            venta.setEstadoPago(Venta.EstadoPago.PAGADO);
        }

        for (ItemCotizacion item : cotizacion.getItems()) {
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
     * Venta directa, sin cotización previa
     * (RF: la empresa también vende sin cotizar antes).
     *
     * Reglas de negocio:
     *  - Para productos con controlaStock() = true, el stock debe ser suficiente (409 si no).
     */
    public Venta registrarVentaDirecta(Long clienteId, List<ItemVentaDTO> items,
                                       Venta.TipoPago tipoPago, Integer diasCredito) {
        log.info("Iniciando transaccion: Registrar Venta directa para Cliente ID {}", clienteId);

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

    public Venta marcarComoPagada(Long ventaId) {
        Venta venta = ventaRepository.findById(ventaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Venta no encontrada: " + ventaId));
        venta.setEstadoPago(Venta.EstadoPago.PAGADO);
        return ventaRepository.save(venta);
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
