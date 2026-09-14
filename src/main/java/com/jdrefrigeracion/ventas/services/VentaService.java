package com.jdrefrigeracion.ventas.services;
import com.jdrefrigeracion.ventas.models.Venta;
import com.jdrefrigeracion.ventas.models.DetalleVenta;
import com.jdrefrigeracion.ventas.repositories.VentaRepository;

import com.jdrefrigeracion.catalogo.models.Producto;
import com.jdrefrigeracion.catalogo.repositories.ProductoRepository;
import com.jdrefrigeracion.clientes.integrations.Cliente;
import com.jdrefrigeracion.clientes.repositories.ClienteRepository;
import com.jdrefrigeracion.cotizaciones.models.Cotizacion;
import com.jdrefrigeracion.cotizaciones.repositories.CotizacionRepository;
import com.jdrefrigeracion.cotizaciones.models.ItemCotizacion;
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
    private final CotizacionRepository cotizacionRepository;
    private final ClienteRepository clienteRepository;
    private final ProductoRepository productoRepository;
    private final com.jdrefrigeracion.mantenimiento.repositories.MantenimientoRepository mantenimientoRepository;
    private final com.jdrefrigeracion.facturacion.services.FacturacionService facturacionService;

    public VentaService(VentaRepository ventaRepository,
                         CotizacionRepository cotizacionRepository,
                         ClienteRepository clienteRepository,
                         ProductoRepository productoRepository,
                         com.jdrefrigeracion.mantenimiento.repositories.MantenimientoRepository mantenimientoRepository,
                         com.jdrefrigeracion.facturacion.services.FacturacionService facturacionService) {
        this.ventaRepository = ventaRepository;
        this.cotizacionRepository = cotizacionRepository;
        this.clienteRepository = clienteRepository;
        this.productoRepository = productoRepository;
        this.mantenimientoRepository = mantenimientoRepository;
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
     */
    public Venta convertirCotizacionEnVenta(Long cotizacionId, Venta.TipoPago tipoPago, Integer diasCredito) {
        log.info("Iniciando transaccion: Convertir Cotizacion {} en Venta", cotizacionId);
        Cotizacion cotizacion = cotizacionRepository.findById(cotizacionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Cotización no encontrada: " + cotizacionId));

        if (cotizacion.getEstado() != Cotizacion.EstadoCotizacion.ACEPTADA) {
            throw new IllegalStateException(
                    "Solo se puede convertir en venta una cotización ACEPTADA. Estado actual: "
                            + cotizacion.getEstado());
        }

        Venta venta = new Venta();
        venta.setCliente(cotizacion.getCliente());
        venta.setCotizacionOrigen(cotizacion);
        venta.setFechaVenta(LocalDate.now());
        venta.setTipoPago(tipoPago);
        venta.setMoneda(Venta.Moneda.valueOf(cotizacion.getMoneda().name()));

        if (tipoPago == Venta.TipoPago.CREDITO) {
            int dias = diasCredito != null ? diasCredito : 30; // 30 días por defecto
            venta.setDiasCredito(dias);
            venta.setFechaLimitePago(LocalDate.now().plusDays(dias));
        } else {
            venta.setEstadoPago(Venta.EstadoPago.PAGADO); // al contado, se paga en el momento
        }

        // Copia cada item de la cotización como detalle de venta,
        // reutilizando el mismo precio que ya se había cotizado
        for (ItemCotizacion item : cotizacion.getItems()) {
            Producto producto = item.getProducto();
            
            // Descuento de inventario (Permite stock negativo según política)
            int nuevoStock = (producto.getStockActual() != null ? producto.getStockActual() : 0) - item.getCantidad();
            producto.setStockActual(nuevoStock);
            productoRepository.save(producto);

            venta.agregarDetalle(new DetalleVenta(
                    producto, item.getCantidad(), item.getPrecioUnitario()));
        }

        Venta ventaGuardada = ventaRepository.save(venta);
        programarMantenimientosSiAplica(ventaGuardada);
        
        try {
            com.jdrefrigeracion.facturacion.models.Comprobante comp = facturacionService.emitirComprobanteParaVenta(ventaGuardada.getId());
            facturacionService.enviarFacturaCorreo(comp.getId());
        } catch (Exception e) {
            log.error("Error al emitir/enviar factura: {}", e.getMessage());
        }

        log.info("Transaccion exitosa: Venta registrada con ID {}", ventaGuardada.getId());
        return ventaGuardada;
    }

    /** Venta directa, sin cotización previa (RF: la empresa también vende sin cotizar antes) */
    public Venta registrarVentaDirecta(Long clienteId, List<ItemVentaDTO> items,
                                        Venta.TipoPago tipoPago, Integer diasCredito) {
        log.info("Iniciando transaccion: Registrar Venta directa para Cliente ID {}", clienteId);
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado: " + clienteId));

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
            Producto producto = productoRepository.findById(itemDTO.productoId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Producto no encontrado: " + itemDTO.productoId()));
            monedaDetectada = producto.getMoneda(); // asumimos 1 sola moneda por venta
            
            // Descuento de inventario (Permite stock negativo según política)
            int nuevoStock = (producto.getStockActual() != null ? producto.getStockActual() : 0) - itemDTO.cantidad();
            producto.setStockActual(nuevoStock);
            productoRepository.save(producto);

            venta.agregarDetalle(new DetalleVenta(
                    producto, itemDTO.cantidad(), producto.calcularPrecioVenta()));
        }
        venta.setMoneda(monedaDetectada != null
                ? Venta.Moneda.valueOf(monedaDetectada.name())
                : Venta.Moneda.SOLES);

        Venta ventaGuardada = ventaRepository.save(venta);
        programarMantenimientosSiAplica(ventaGuardada);
        
        try {
            com.jdrefrigeracion.facturacion.models.Comprobante comp = facturacionService.emitirComprobanteParaVenta(ventaGuardada.getId());
            facturacionService.enviarFacturaCorreo(comp.getId());
        } catch (Exception e) {
            log.error("Error al emitir/enviar factura: {}", e.getMessage());
        }

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
                .orElseThrow(() -> new IllegalArgumentException("Venta no encontrada: " + ventaId));
        venta.setEstadoPago(Venta.EstadoPago.PAGADO);
        return ventaRepository.save(venta);
    }

    public record ItemVentaDTO(Long productoId, Integer cantidad) {}

    private void programarMantenimientosSiAplica(Venta venta) {
        boolean requiereMantenimiento = venta.getDetalles().stream()
                .anyMatch(d -> d.getProducto().getSector() == Producto.Sector.CLIMATIZACION ||
                               d.getProducto().getSector() == Producto.Sector.REFRIGERACION);
        
        if (requiereMantenimiento) {
            for (int i = 1; i <= 4; i++) {
                com.jdrefrigeracion.mantenimiento.models.Mantenimiento mant = new com.jdrefrigeracion.mantenimiento.models.Mantenimiento();
                mant.setVenta(venta);
                mant.setFechaProgramada(venta.getFechaVenta().plusMonths(3L * i));
                mantenimientoRepository.save(mant);
            }
        }
    }
}
