package com.bomerp.ventas.venta.service;

import com.bomerp.config.descuento.TopeDescuentoService;
import com.bomerp.config.tipocambio.TipoCambioService;
import java.math.BigDecimal;
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
    private final TipoCambioService tipoCambioService;
    private final TopeDescuentoService topeDescuentoService;

    public VentaService(VentaRepository ventaRepository,
                        ProformaService proformaService,
                        ClienteService clienteService,
                        ProductoService productoService,
                        MantenimientoService mantenimientoService,
                        FacturacionService facturacionService,
                        TipoCambioService tipoCambioService,
                        TopeDescuentoService topeDescuentoService) {
        this.ventaRepository = ventaRepository;
        this.proformaService = proformaService;
        this.clienteService = clienteService;
        this.productoService = productoService;
        this.mantenimientoService = mantenimientoService;
        this.facturacionService = facturacionService;
        this.tipoCambioService = tipoCambioService;
        this.topeDescuentoService = topeDescuentoService;
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
        return convertirProformaEnVenta(proformaId, tipoPago, diasCredito, null, null);
    }

    /** @param vendedorId sub del JWT de quien registra · @param vendedorNombre su nombre de usuario */
    public Venta convertirProformaEnVenta(Long proformaId, Venta.TipoPago tipoPago, Integer diasCredito,
                                          String vendedorId, String vendedorNombre) {
        log.info("Iniciando transaccion: Convertir Proforma {} en Venta", proformaId);

        Proforma proforma = proformaService.obtenerPorId(proformaId);

        // ── Regla 1: Solo ACEPTADA se puede convertir ──────────────────────
        if (proforma.getEstado() == Proforma.EstadoProforma.CONVERTIDA) {
            throw new ReglaNegocioException("La proforma #" + proformaId + " ya fue convertida en la venta #"
                    + proforma.getVentaId() + ".");
        }
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
        venta.setVendedorId(vendedorId);
        venta.setVendedorNombre(vendedorNombre);
        venta.setTipoPago(tipoPago);
        venta.setMoneda(Venta.Moneda.valueOf(proforma.getMoneda().name()));
        // Se respeta el monto cotizado: mismo tipo de cambio que la proforma, aunque hoy sea otro
        venta.setTipoCambio(proforma.getTipoCambio());

        aplicarFormaPago(venta, tipoPago, diasCredito);

        for (ItemProforma item : proforma.getItems()) {
            Producto producto = item.getProducto();

            // Descuento de inventario (solo para productos que controlan stock)
            if (producto.controlaStock()) {
                int nuevoStock = (producto.getStockActual() != null ? producto.getStockActual() : 0)
                        - item.getCantidad();
                producto.setStockActual(nuevoStock);
                productoService.guardar(producto);
            }

            // Se respeta también el descuento negociado en la proforma
            venta.agregarDetalle(new DetalleVenta(
                    producto, item.getCantidad(), item.getPrecioUnitario(), item.getDescuentoPorcentaje()));
        }

        Venta ventaGuardada = ventaRepository.save(venta);
        proformaService.marcarConvertida(proformaId, ventaGuardada.getId());
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
        return registrarVentaDirecta(clienteId, items, tipoPago, diasCredito, null, null);
    }

    public Venta registrarVentaDirecta(Long clienteId, List<ItemVentaDTO> items,
                                       Venta.TipoPago tipoPago, Integer diasCredito,
                                       Venta.Moneda monedaVenta, BigDecimal tipoCambio) {
        return registrarVentaDirecta(clienteId, items, tipoPago, diasCredito, monedaVenta, tipoCambio, null, null);
    }

    /**
     * @param monedaVenta moneda en la que se cobra (null = la del primer producto)
     * @param tipoCambio  soles por dólar negociado (null = venta SUNAT del día)
     */
    public Venta registrarVentaDirecta(Long clienteId, List<ItemVentaDTO> items,
                                       Venta.TipoPago tipoPago, Integer diasCredito,
                                       Venta.Moneda monedaVenta, BigDecimal tipoCambio,
                                       String vendedorId, String vendedorNombre) {
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
        List<BigDecimal> descuentos = items.stream()
                .map(i -> topeDescuentoService.validar(i.descuentoPorcentaje())).toList();

        // ── Regla: stock suficiente, antes de mutar nada ────────────────────
        List<Producto> productos = new java.util.ArrayList<>();
        for (ItemVentaDTO itemDTO : items) {
            Producto producto = productoService.obtenerPorId(itemDTO.productoId());
            productos.add(producto);
            if (producto.controlaStock()) {
                int stockDisponible = producto.getStockActual() != null ? producto.getStockActual() : 0;
                if (stockDisponible < itemDTO.cantidad()) {
                    throw new StockInsuficienteException(
                            producto.getNombre(), stockDisponible, itemDTO.cantidad());
                }
            }
        }

        // ── Moneda y tipo de cambio ──────────────────────────────────────────
        // Se puede vender en soles o en dólares sin importar la moneda de cada producto:
        // los precios se convierten con el tipo de cambio, que queda guardado en la venta.
        Venta.Moneda moneda = monedaVenta != null ? monedaVenta
                : Venta.Moneda.valueOf(productos.get(0).getMoneda().name());
        boolean requiereConversion = productos.stream().anyMatch(p -> !p.getMoneda().name().equals(moneda.name()));
        BigDecimal tc = requiereConversion
                ? tipoCambioService.resolver(tipoCambio)              // obligatorio: 503 si SUNAT no responde y no se escribió
                : tipoCambioService.resolverSiSePuede(tipoCambio);    // solo como referencia

        Venta venta = new Venta();
        venta.setCliente(cliente);
        venta.setFechaVenta(LocalDate.now());
        venta.setVendedorId(vendedorId);
        venta.setVendedorNombre(vendedorNombre);
        venta.setTipoPago(tipoPago);
        venta.setMoneda(moneda);
        venta.setTipoCambio(tc);

        aplicarFormaPago(venta, tipoPago, diasCredito);

        for (int i = 0; i < items.size(); i++) {
            ItemVentaDTO itemDTO = items.get(i);
            Producto producto = productos.get(i);

            if (producto.controlaStock()) {
                int nuevoStock = (producto.getStockActual() != null ? producto.getStockActual() : 0)
                        - itemDTO.cantidad();
                producto.setStockActual(nuevoStock);
                productoService.guardar(producto);
            }

            BigDecimal precio = TipoCambioService.convertir(producto.calcularPrecioVenta(),
                    producto.getMoneda().name(), moneda.name(), tc);
            venta.agregarDetalle(new DetalleVenta(producto, itemDTO.cantidad(), precio, descuentos.get(i)));
        }

        Venta ventaGuardada = ventaRepository.save(venta);
        mantenimientoService.programarParaVenta(ventaGuardada);

        emitirFactura(ventaGuardada);

        log.info("Transaccion exitosa: Venta registrada con ID {} en {} (tipo de cambio {})",
                ventaGuardada.getId(), moneda, tc);
        return ventaGuardada;
    }

    /** RNF: seguimiento de cobranza — ventas a crédito vencidas */
    public List<Venta> listarVentasConPagoAtrasado() {
        return ventaRepository.findByEstadoAndEstadoPagoAndFechaLimitePagoBefore(
                Venta.EstadoVenta.REGISTRADA, Venta.EstadoPago.PENDIENTE, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public List<Venta> buscar(Venta.EstadoVenta estado, LocalDate desde, LocalDate hasta) {
        return buscar(estado, desde, hasta, null);
    }

    /** Igual que el reporte, con filtro opcional por cliente (historial del cliente). */
    @Transactional(readOnly = true)
    public List<Venta> buscar(Venta.EstadoVenta estado, LocalDate desde, LocalDate hasta, Long clienteId) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new IllegalArgumentException("La fecha 'desde' no puede ser posterior a 'hasta'.");
        }
        if (clienteId != null) {
            clienteService.obtenerPorId(clienteId);   // 404 si el cliente no existe
        }
        return ventaRepository.buscar(estado, desde, hasta, clienteId);
    }

    /** Fachada para el kardex (Inventario): líneas vendidas de un producto. */
    @Transactional(readOnly = true)
    public List<DetalleVenta> detallesDeProducto(Long productoId) {
        return ventaRepository.detallesDeProducto(productoId);
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
     *  - Efectos colaterales, en la misma transacción:
     *    1) se devuelve al inventario lo vendido de los productos con controlaStock() = true
     *       (los equipos no se almacenan, no se restauran);
     *    2) Postventa cancela los mantenimientos aún pendientes de esa venta;
     *    3) Facturación emite la nota de crédito que anula el comprobante aceptado.
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
        venta.setFechaAnulacion(LocalDate.now());

        int mantenimientosCancelados = mantenimientoService.cancelarPendientesDeVenta(ventaId);
        String notaCredito = facturacionService
                .emitirNotaCreditoPorAnulacion(ventaId, "Anulación de la operación (venta #" + ventaId + ")")
                .map(nc -> nc.getSerie() + "-" + nc.getNumero())
                .orElse("no requerida");

        log.info("Venta {} anulada: stock restaurado, {} mantenimientos cancelados, nota de crédito {}",
                ventaId, mantenimientosCancelados, notaCredito);
        return ventaRepository.save(venta);
    }

    /** Fachada para Cobranza: ventas REGISTRADAS aún no pagadas (opcional: de un cliente). */
    @Transactional(readOnly = true)
    public List<Venta> listarPendientesDeCobro(Long clienteId) {
        return ventaRepository.findByEstadoAndEstadoPago(Venta.EstadoVenta.REGISTRADA, Venta.EstadoPago.PENDIENTE)
                .stream()
                .filter(v -> clienteId == null || v.getCliente().getId().equals(clienteId))
                .toList();
    }

    /** Fachada para otros módulos (Cobranza): 404 si la venta no existe. */
    @Transactional(readOnly = true)
    public Venta obtenerPorIdPublico(Long ventaId) {
        return obtenerPorId(ventaId);
    }

    private Venta obtenerPorId(Long ventaId) {
        return ventaRepository.findById(ventaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Venta no encontrada: " + ventaId));
    }

    /** descuentoPorcentaje: opcional, % negociado de la línea (0 a tope del rol). */
    public record ItemVentaDTO(Long productoId, Integer cantidad, BigDecimal descuentoPorcentaje) {}

    /** Emite y envía la factura electrónica; los errores no abortan la venta. */
    private void emitirFactura(Venta venta) {
        com.bomerp.facturacion.comprobante.entity.Comprobante comp;
        try {
            comp = facturacionService.emitirComprobanteParaVenta(venta.getId());
        } catch (Exception e) {
            log.error("Error al emitir el comprobante de la Venta {}: {}", venta.getId(), e.getMessage());
            return;
        }
        // Sin correo no es un error: el comprobante queda emitido y se descarga o reenvía desde Ventas
        String correo = venta.getCliente().getCorreo();
        if (correo == null || correo.isBlank()) {
            log.info("Comprobante de la Venta {} emitido; no se envía por correo: el cliente no tiene correo", venta.getId());
            return;
        }
        try {
            facturacionService.enviarFacturaCorreo(comp.getId());
        } catch (Exception e) {
            log.warn("Comprobante de la Venta {} emitido, pero no se pudo enviar por correo: {}", venta.getId(), e.getMessage());
        }
    }

    /**
     * Contado: la venta queda PAGADA. Crédito: queda pendiente con fecha límite
     * (30 días si no se indican; deben estar entre 1 y 365).
     */
    private void aplicarFormaPago(Venta venta, Venta.TipoPago tipoPago, Integer diasCredito) {
        if (tipoPago == Venta.TipoPago.CREDITO) {
            int dias = diasCredito != null ? diasCredito : 30;
            if (dias < 1 || dias > 365) {
                throw new IllegalArgumentException("Los días de crédito deben estar entre 1 y 365.");
            }
            venta.setDiasCredito(dias);
            venta.setFechaLimitePago(LocalDate.now().plusDays(dias));
        } else {
            venta.setEstadoPago(Venta.EstadoPago.PAGADO);
        }
    }
}
