package com.bomerp.ventas.venta.service;

import com.bomerp.seguridad.descuento.TopeDescuentoService;
import com.bomerp.config.tipocambio.TipoCambioService;
import com.bomerp.ventas.tributos.DeduccionTributariaService;
import java.math.BigDecimal;
import com.bomerp.ventas.venta.dto.VentaResponseDTO;
import com.bomerp.ventas.venta.entity.Venta;
import com.bomerp.ventas.venta.event.VentaAnuladaEvent;
import com.bomerp.ventas.venta.event.VentaRegistradaEvent;
import com.bomerp.ventas.venta.event.RutinaMantenimiento;
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

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bomerp.config.paginacion.Orden;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Service
@Transactional
@Slf4j
public class VentaService {

    private final VentaRepository ventaRepository;
    private final ProformaService proformaService;
    private final ClienteService clienteService;
    private final ProductoService productoService;
    private final TipoCambioService tipoCambioService;
    private final TopeDescuentoService topeDescuentoService;
    private final DeduccionTributariaService deduccionTributariaService;
    private final ApplicationEventPublisher eventos;

    public VentaService(VentaRepository ventaRepository,
                        ProformaService proformaService,
                        ClienteService clienteService,
                        ProductoService productoService,
                        TipoCambioService tipoCambioService,
                        TopeDescuentoService topeDescuentoService,
                        DeduccionTributariaService deduccionTributariaService,
                        ApplicationEventPublisher eventos) {
        this.ventaRepository = ventaRepository;
        this.proformaService = proformaService;
        this.clienteService = clienteService;
        this.productoService = productoService;
        this.tipoCambioService = tipoCambioService;
        this.topeDescuentoService = topeDescuentoService;
        this.deduccionTributariaService = deduccionTributariaService;
        this.eventos = eventos;
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
    public VentaResponseDTO convertirProformaEnVenta(Long proformaId, Venta.TipoPago tipoPago, Integer diasCredito) {
        return convertirProformaEnVenta(proformaId, tipoPago, diasCredito, null, null);
    }

    public VentaResponseDTO convertirProformaEnVenta(Long proformaId, Venta.TipoPago tipoPago, Integer diasCredito,
                                                     String vendedorId, String vendedorNombre) {
        return convertirProformaEnVenta(proformaId, tipoPago, diasCredito, vendedorId, vendedorNombre, null);
    }

    /**
     * @param vendedorId sub del JWT de quien registra · @param vendedorNombre su nombre de usuario
     * @param rutina     mantenimientos que se programan con la venta (null = ninguno)
     */
    public VentaResponseDTO convertirProformaEnVenta(Long proformaId, Venta.TipoPago tipoPago, Integer diasCredito,
                                                     String vendedorId, String vendedorNombre,
                                                     RutinaMantenimiento rutina) {
        log.info("Iniciando transaccion: Convertir Proforma {} en Venta", proformaId);

        Proforma proforma = proformaService.obtenerParaConvertir(proformaId);   // bloqueada hasta el final

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

        // ── Regla 3: stock suficiente (sumando el mismo producto en varias líneas) y descuento,
        //    con los productos bloqueados para que otra venta simultánea no use el mismo stock ────
        java.util.Map<Long, Integer> cantidades = new java.util.HashMap<>();
        for (ItemProforma item : proforma.getItems()) {
            cantidades.merge(item.getProducto().getId(), item.getCantidad(), Integer::sum);
        }
        productoService.descontarStock(cantidades);

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
            // Se respeta también el descuento negociado en la proforma
            venta.agregarDetalle(new DetalleVenta(
                    producto, item.getCantidad(), item.getPrecioUnitario(), item.getDescuentoPorcentaje()));
        }

        deduccionTributariaService.aplicar(venta);
        RutinaMantenimiento rutinaValida = validarRutina(venta, rutina);
        Venta ventaGuardada = ventaRepository.save(venta);
        proformaService.marcarConvertida(proformaId, ventaGuardada.getId());
        // Postventa programa los mantenimientos y Facturación emite el comprobante (ADR-002)
        eventos.publishEvent(new VentaRegistradaEvent(ventaGuardada.getId(), rutinaValida));

        log.info("Transaccion exitosa: Venta registrada con ID {}", ventaGuardada.getId());
        return VentaResponseDTO.desdeEntidad(ventaGuardada);
    }

    /**
     * Venta directa, sin proforma previa
     * (RF: la empresa también vende sin cotizar antes).
     *
     * Reglas de negocio:
     *  - Para productos con controlaStock() = true, el stock debe ser suficiente (409 si no).
     */
    public VentaResponseDTO registrarVentaDirecta(Long clienteId, List<ItemVentaDTO> items,
                                                  Venta.TipoPago tipoPago, Integer diasCredito) {
        return registrarVentaDirecta(clienteId, items, tipoPago, diasCredito, null, null);
    }

    public VentaResponseDTO registrarVentaDirecta(Long clienteId, List<ItemVentaDTO> items,
                                                  Venta.TipoPago tipoPago, Integer diasCredito,
                                                  Venta.Moneda monedaVenta, BigDecimal tipoCambio) {
        return registrarVentaDirecta(clienteId, items, tipoPago, diasCredito, monedaVenta, tipoCambio, null, null);
    }

    public VentaResponseDTO registrarVentaDirecta(Long clienteId, List<ItemVentaDTO> items,
                                                  Venta.TipoPago tipoPago, Integer diasCredito,
                                                  Venta.Moneda monedaVenta, BigDecimal tipoCambio,
                                                  String vendedorId, String vendedorNombre) {
        return registrarVentaDirecta(clienteId, items, tipoPago, diasCredito, monedaVenta, tipoCambio,
                vendedorId, vendedorNombre, null);
    }

    /**
     * @param monedaVenta moneda en la que se cobra (null = la del primer producto)
     * @param tipoCambio  soles por dólar negociado (null = venta SUNAT del día)
     * @param rutina      mantenimientos que se programan con la venta (null = ninguno)
     */
    public VentaResponseDTO registrarVentaDirecta(Long clienteId, List<ItemVentaDTO> items,
                                                  Venta.TipoPago tipoPago, Integer diasCredito,
                                                  Venta.Moneda monedaVenta, BigDecimal tipoCambio,
                                                  String vendedorId, String vendedorNombre,
                                                  RutinaMantenimiento rutina) {
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

        // ── Regla: stock suficiente sumando el mismo producto en varias líneas, y descuento con los
        //    productos bloqueados (otra venta simultánea espera su turno). Si algo falla después,
        //    la transacción devuelve el stock. ────────────────────────────────
        List<Producto> productos = new java.util.ArrayList<>();
        java.util.Map<Long, Integer> cantidades = new java.util.HashMap<>();
        for (ItemVentaDTO itemDTO : items) {
            productos.add(productoService.obtenerPorId(itemDTO.productoId()));
            cantidades.merge(itemDTO.productoId(), itemDTO.cantidad(), Integer::sum);
        }
        productoService.descontarStock(cantidades);

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

            BigDecimal precio = TipoCambioService.convertir(producto.calcularPrecioVenta(),
                    producto.getMoneda().name(), moneda.name(), tc);
            venta.agregarDetalle(new DetalleVenta(producto, itemDTO.cantidad(), precio, descuentos.get(i)));
        }

        deduccionTributariaService.aplicar(venta);
        RutinaMantenimiento rutinaValida = validarRutina(venta, rutina);
        Venta ventaGuardada = ventaRepository.save(venta);
        // Postventa programa los mantenimientos y Facturación emite el comprobante (ADR-002)
        eventos.publishEvent(new VentaRegistradaEvent(ventaGuardada.getId(), rutinaValida));

        log.info("Transaccion exitosa: Venta registrada con ID {} en {} (tipo de cambio {})",
                ventaGuardada.getId(), moneda, tc);
        return VentaResponseDTO.desdeEntidad(ventaGuardada);
    }

    /**
     * Rutina elegida con el botón "Implementar rutinas de mantenimiento": solo para ventas con equipos de
     * climatización o refrigeración, cada 1 a 12 meses, de 1 a 24 visitas y la primera desde el día
     * de la venta (sin fecha: la venta más la frecuencia). Devuelve la rutina con la primera fecha resuelta.
     */
    static RutinaMantenimiento validarRutina(Venta venta, RutinaMantenimiento rutina) {
        if (rutina == null) return null;
        boolean conEquipos = venta.getDetalles().stream().anyMatch(d -> d.getProducto().requiereMantenimiento());
        if (!conEquipos) {
            throw new ReglaNegocioException("Las rutinas de mantenimiento son para ventas con equipos de "
                    + "climatización o refrigeración.");
        }
        Integer frecuencia = rutina.frecuenciaMeses();
        if (frecuencia == null || frecuencia < 1 || frecuencia > RutinaMantenimiento.MAX_FRECUENCIA_MESES) {
            throw new IllegalArgumentException("La frecuencia del mantenimiento debe estar entre 1 y "
                    + RutinaMantenimiento.MAX_FRECUENCIA_MESES + " meses.");
        }
        Integer visitas = rutina.visitas();
        if (visitas == null || visitas < 1 || visitas > RutinaMantenimiento.MAX_VISITAS) {
            throw new IllegalArgumentException("La rutina debe tener entre 1 y " + RutinaMantenimiento.MAX_VISITAS + " visitas.");
        }
        LocalDate primera = rutina.primeraVisita() != null ? rutina.primeraVisita()
                : venta.getFechaVenta().plusMonths(frecuencia);
        if (primera.isBefore(venta.getFechaVenta())) {
            throw new IllegalArgumentException("La primera visita no puede ser anterior a la fecha de la venta.");
        }
        return new RutinaMantenimiento(frecuencia, visitas, primera);
    }

    /** RNF: seguimiento de cobranza — ventas a crédito vencidas */
    @Transactional(readOnly = true)
    public List<VentaResponseDTO> listarVentasConPagoAtrasado() {
        return ventaRepository.findByEstadoAndEstadoPagoAndFechaLimitePagoBefore(
                        Venta.EstadoVenta.REGISTRADA, Venta.EstadoPago.PENDIENTE, LocalDate.now())
                .stream().map(VentaResponseDTO::desdeEntidad).toList();
    }

    /**
     * Reporte de ventas para el controller. El DTO lee los detalles (LAZY), así que se arma aquí,
     * dentro de la transacción, y no en el controller (guía S3, 2.7).
     */
    @Transactional(readOnly = true)
    public List<VentaResponseDTO> listar(Venta.EstadoVenta estado, LocalDate desde, LocalDate hasta, Long clienteId) {
        return buscar(estado, desde, hasta, clienteId).stream().map(VentaResponseDTO::desdeEntidad).toList();
    }

    @Transactional(readOnly = true)
    public List<Venta> buscar(Venta.EstadoVenta estado, LocalDate desde, LocalDate hasta) {
        return buscar(estado, desde, hasta, null);
    }

    /** Igual que el reporte, con filtro opcional por cliente (historial del cliente). */
    @Transactional(readOnly = true)
    /** Totales de las ventas REGISTRADAS de una moneda (las anuladas no son ingreso). */
    public record ResumenMoneda(String moneda, long cantidad, BigDecimal montoTotal, BigDecimal ticketPromedio) {}

    /** Una página del reporte, con el resumen calculado sobre TODAS las ventas del filtro (no solo la página). */
    public record ReporteVentas(com.bomerp.config.paginacion.Pagina<VentaResponseDTO> ventas, List<ResumenMoneda> resumen,
                                long anuladas) {}

    /** Orden de siempre: la más nueva primero. */
    private static final Comparator<Venta> RECIENTES_PRIMERO =
            Comparator.comparing(Venta::getFechaVenta).thenComparing(Venta::getId).reversed();

    /**
     * Columnas que la pantalla de Ventas puede ordenar (clic en el encabezado). El total se compara en
     * soles, con el tipo de cambio de cada venta en dólares (o el de hoy, si la venta no lo guardó).
     */
    private static Map<String, Orden.Campo<Venta>> camposOrden(BigDecimal tipoCambioHoy) {
        return Map.of(
                "id", Orden.valor(Venta::getId),
                "cliente", Orden.texto((Venta v) -> v.getCliente().getNombreORazonSocial()),
                "fecha", Orden.valor(Venta::getFechaVenta),
                "estado", Orden.texto((Venta v) -> v.getEstado().name()),
                "total", Orden.valor((Venta v) -> Orden.enSoles(v.calcularTotal(), v.getMoneda() == Venta.Moneda.DOLARES,
                        v.getTipoCambio(), tipoCambioHoy)));
    }

    /** @param orden columna y dirección, p. ej. "cliente,asc" (null = la más nueva primero) */
    @Transactional(readOnly = true)
    public ReporteVentas reportePagina(Venta.EstadoVenta estado, LocalDate desde, LocalDate hasta, Long clienteId,
                                       Integer pagina, Integer tamano, String orden) {
        List<Venta> todas = buscar(estado, desde, hasta, clienteId);
        List<ResumenMoneda> resumen = new java.util.ArrayList<>();
        for (Venta.Moneda moneda : Venta.Moneda.values()) {
            List<Venta> vigentes = todas.stream()
                    .filter(v -> v.getEstado() == Venta.EstadoVenta.REGISTRADA && v.getMoneda() == moneda).toList();
            if (vigentes.isEmpty()) continue;
            BigDecimal monto = vigentes.stream().map(Venta::calcularTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
            resumen.add(new ResumenMoneda(moneda.name(), vigentes.size(), monto,
                    monto.divide(BigDecimal.valueOf(vigentes.size()), 2, java.math.RoundingMode.HALF_UP)));
        }
        long anuladas = todas.stream().filter(v -> v.getEstado() == Venta.EstadoVenta.ANULADA).count();
        BigDecimal tipoCambioHoy = Orden.pide(orden, "total") ? tipoCambioService.resolverSiSePuede(null) : null;
        List<Venta> ordenadas = Orden.ordenar(todas, orden, camposOrden(tipoCambioHoy), RECIENTES_PRIMERO);
        return new ReporteVentas(com.bomerp.config.paginacion.Pagina.deLista(ordenadas,
                com.bomerp.config.paginacion.Pagina.pedido(pagina, tamano), VentaResponseDTO::desdeEntidad), resumen, anuladas);
    }

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

    /**
     * Fachada para Cobranza: la venta pasa a PAGADO cuando sus abonos cubren el total.
     * No tiene endpoint propio (auditoría A7): un pago siempre entra como cobro.
     */
    public VentaResponseDTO marcarComoPagada(Long ventaId) {
        Venta venta = obtenerPorId(ventaId);
        if (venta.getEstado() == Venta.EstadoVenta.ANULADA) {
            throw new ReglaNegocioException("No se puede cobrar la venta #" + ventaId + ": está ANULADA.");
        }
        if (venta.getEstadoPago() == Venta.EstadoPago.PAGADO) {
            throw new ReglaNegocioException("La venta #" + ventaId + " ya está PAGADA.");
        }
        venta.setEstadoPago(Venta.EstadoPago.PAGADO);
        return VentaResponseDTO.desdeEntidad(ventaRepository.save(venta));
    }

    /**
     * Fachada para Cobranza: al anular un cobro, la venta que había quedado PAGADA por él
     * vuelve a PENDIENTE (su saldo ya no está cubierto).
     */
    public void volverAPendiente(Long ventaId) {
        Venta venta = obtenerPorId(ventaId);
        if (venta.getEstado() == Venta.EstadoVenta.ANULADA) {
            throw new ReglaNegocioException("La venta #" + ventaId + " está ANULADA: su dinero se resuelve en Devoluciones.");
        }
        venta.setEstadoPago(Venta.EstadoPago.PENDIENTE);
        ventaRepository.save(venta);
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
     *    2) con VentaAnuladaEvent: Postventa cancela los mantenimientos aún pendientes, Facturación
     *       emite la nota de crédito y Cobranza deja pendiente la devolución de lo ya pagado.
     */
    public VentaResponseDTO anular(Long ventaId) {
        // Fila bloqueada hasta el fin de la transacción (revisión externa): dos anulaciones simultáneas
        // de la misma venta devolverían el stock dos veces; la segunda espera y encuentra ANULADA (409).
        Venta venta = ventaRepository.bloquear(ventaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Venta no encontrada: " + ventaId));
        if (venta.getEstado() == Venta.EstadoVenta.ANULADA) {
            throw new ReglaNegocioException("La venta #" + ventaId + " ya está ANULADA.");
        }

        java.util.Map<Long, Integer> devueltas = new java.util.HashMap<>();
        for (DetalleVenta detalle : venta.getDetalles()) {
            devueltas.merge(detalle.getProducto().getId(), detalle.getCantidad(), Integer::sum);
        }
        productoService.sumarStock(devueltas);
        venta.setEstado(Venta.EstadoVenta.ANULADA);
        venta.setFechaAnulacion(LocalDate.now());

        Venta anulada = ventaRepository.save(venta);
        eventos.publishEvent(new VentaAnuladaEvent(ventaId));

        log.info("Venta {} anulada: stock restaurado", ventaId);
        return VentaResponseDTO.desdeEntidad(anulada);
    }

    /** Fachada para Cobranza: ventas REGISTRADAS aún no pagadas (opcional: de un cliente). */
    @Transactional(readOnly = true)
    public List<Venta> listarPendientesDeCobro(Long clienteId) {
        return ventaRepository.findByEstadoAndEstadoPago(Venta.EstadoVenta.REGISTRADA, Venta.EstadoPago.PENDIENTE)
                .stream()
                .filter(v -> clienteId == null || v.getCliente().getId().equals(clienteId))
                .toList();
    }

    /**
     * Fachada para Cobranza: la venta con su fila bloqueada hasta el fin de la transacción, para que
     * dos cobros simultáneos no superen juntos el saldo. 404 si no existe.
     */
    public Venta obtenerParaCobro(Long ventaId) {
        return ventaRepository.bloquear(ventaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Venta no encontrada: " + ventaId));
    }

    /** Fachada para otros módulos (Cobranza): 404 si la venta no existe. */
    @Transactional(readOnly = true)
    public Venta obtenerPorIdPublico(Long ventaId) {
        return obtenerPorId(ventaId);
    }

    /** Fachada para Facturación: guarda en la venta el comprobante que se le emitió. */
    public void asignarComprobante(Long ventaId, Long comprobanteId) {
        obtenerPorId(ventaId).setComprobanteId(comprobanteId);
    }

    /** Fachada para Reportes: ventas REGISTRADAS (no anuladas) entre dos fechas. */
    @Transactional(readOnly = true)
    public List<Venta> registradasEntre(LocalDate desde, LocalDate hasta) {
        return ventaRepository.findByEstadoAndFechaVentaBetween(Venta.EstadoVenta.REGISTRADA, desde, hasta);
    }

    /** Fachada para Reportes: ventas REGISTRADAS sin pagar cuya fecha límite ya pasó. */
    @Transactional(readOnly = true)
    public List<Venta> pendientesVencidasAl(LocalDate dia) {
        return ventaRepository.findByEstadoAndEstadoPagoAndFechaLimitePagoBefore(
                Venta.EstadoVenta.REGISTRADA, Venta.EstadoPago.PENDIENTE, dia);
    }

    /** Fachada para Rentabilidad: líneas de las ventas no anuladas del periodo (con venta, cliente y producto). */
    @Transactional(readOnly = true)
    public List<DetalleVenta> lineasVendidas(LocalDate desde, LocalDate hasta) {
        return ventaRepository.lineasVendidas(desde, hasta);
    }

    /** Fachada para Rentabilidad: líneas de unas ventas (las que facturaron órdenes de trabajo). */
    @Transactional(readOnly = true)
    public List<DetalleVenta> lineasDeVentas(java.util.Collection<Long> ids) {
        return ventaRepository.lineasDeVentas(ids);
    }

    private Venta obtenerPorId(Long ventaId) {
        return ventaRepository.findById(ventaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Venta no encontrada: " + ventaId));
    }

    /** descuentoPorcentaje: opcional, % negociado de la línea (0 a tope del rol). */
    public record ItemVentaDTO(Long productoId, Integer cantidad, BigDecimal descuentoPorcentaje) {}

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
