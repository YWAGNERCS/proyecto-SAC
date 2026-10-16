package com.bomerp.ventas.reporte.service;

import com.bomerp.clientes.cliente.service.ClienteService;
import com.bomerp.clientes.general.integration.Cliente;
import com.bomerp.config.exportacion.ExcelBuilder;
import com.bomerp.facturacion.facturacion.service.FacturacionService;
import com.bomerp.ventas.detalleventa.entity.DetalleVenta;
import com.bomerp.ventas.venta.dto.VentaResponseDTO;
import com.bomerp.ventas.venta.entity.Venta;
import com.bomerp.ventas.venta.service.VentaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Reportes del módulo Ventas: exportación a Excel e historial por cliente.
 * Cada módulo expone sus propias consultas (decisión del equipo); este servicio solo
 * usa fachadas públicas: VentaService, ClienteService y FacturacionService.
 */
@Service
@Transactional(readOnly = true)
public class VentaReporteService {

    private final VentaService ventaService;
    private final ClienteService clienteService;
    private final FacturacionService facturacionService;

    public VentaReporteService(VentaService ventaService, ClienteService clienteService,
                               FacturacionService facturacionService) {
        this.ventaService = ventaService;
        this.clienteService = clienteService;
        this.facturacionService = facturacionService;
    }

    /**
     * Excel con los mismos filtros del reporte de la SPA. Dos hojas:
     * "Ventas" (una fila por venta) y "Detalle" (una fila por producto vendido).
     */
    public byte[] exportarExcel(Venta.EstadoVenta estado, LocalDate desde, LocalDate hasta, Long clienteId) {
        List<Venta> ventas = ventaService.buscar(estado, desde, hasta, clienteId);
        String filtros = ExcelBuilder.describirPeriodo(desde, hasta)
                + (estado != null ? " · estado " + estado : "")
                + (clienteId != null ? " · cliente " + clienteService.obtenerPorId(clienteId).getNombreORazonSocial() : "");

        ExcelBuilder excel = new ExcelBuilder();
        ExcelBuilder.Hoja hojaVentas = excel.hoja("Ventas", "Reporte de ventas", filtros,
                ExcelBuilder.entero("N° venta"), ExcelBuilder.fecha("Fecha"), ExcelBuilder.texto("Cliente"),
                ExcelBuilder.texto("RUC / DNI"), ExcelBuilder.texto("Tipo de pago"), ExcelBuilder.entero("Días de crédito"),
                ExcelBuilder.fecha("Fecha límite de pago"), ExcelBuilder.texto("Estado"), ExcelBuilder.fecha("Fecha de anulación"),
                ExcelBuilder.texto("Estado de pago"), ExcelBuilder.texto("Origen"), ExcelBuilder.texto("Comprobante"),
                ExcelBuilder.texto("Nota de crédito"), ExcelBuilder.texto("Moneda"), ExcelBuilder.moneda("Subtotal"),
                ExcelBuilder.moneda("IGV (18%)"), ExcelBuilder.moneda("Total"));
        ExcelBuilder.Hoja hojaDetalle = excel.hoja("Detalle", "Detalle de productos vendidos", filtros,
                ExcelBuilder.entero("N° venta"), ExcelBuilder.fecha("Fecha"), ExcelBuilder.texto("Cliente"),
                ExcelBuilder.texto("Estado"), ExcelBuilder.texto("Producto"), ExcelBuilder.texto("Categoría"),
                ExcelBuilder.texto("Sector"), ExcelBuilder.entero("Cantidad"), ExcelBuilder.moneda("Precio unitario"),
                ExcelBuilder.moneda("Subtotal"), ExcelBuilder.texto("Moneda"));

        for (Venta v : ventas) {
            hojaVentas.fila(v.getId(), v.getFechaVenta(), v.getCliente().getNombreORazonSocial(), documento(v.getCliente()),
                    v.getTipoPago(), v.getDiasCredito(), v.getFechaLimitePago(), v.getEstado(), v.getFechaAnulacion(),
                    v.getEstadoPago(), v.getProformaOrigen() != null ? "Proforma #" + v.getProformaOrigen().getId() : "Directa",
                    facturacionService.numeroComprobanteDeVenta(v.getId()).orElse(null),
                    facturacionService.numeroNotaCreditoDeVenta(v.getId()).orElse(null),
                    v.getMoneda(), v.calcularSubtotal(), v.calcularIgv(), v.calcularTotal());
            for (DetalleVenta d : v.getDetalles()) {
                hojaDetalle.fila(v.getId(), v.getFechaVenta(), v.getCliente().getNombreORazonSocial(), v.getEstado(),
                        d.getProducto().getNombre(),
                        d.getProducto().getCategoria() != null ? d.getProducto().getCategoria().getNombre() : null,
                        d.getProducto().getSector(), d.getCantidad(), d.getPrecioUnitario(),
                        d.calcularSubtotalDetalle(), v.getMoneda());
            }
        }

        // Totales vigentes por moneda (las ANULADAS no cuentan como ingreso)
        for (Venta.Moneda moneda : Venta.Moneda.values()) {
            List<Venta> vigentes = ventas.stream()
                    .filter(v -> v.getEstado() == Venta.EstadoVenta.REGISTRADA && v.getMoneda() == moneda).toList();
            if (!vigentes.isEmpty()) {
                hojaVentas.filaTotal("Total vigente (" + vigentes.size() + " ventas, sin anuladas)", null, null, null, null,
                        null, null, null, null, null, null, null, null, moneda,
                        sumar(vigentes, Venta::calcularSubtotal), sumar(vigentes, Venta::calcularIgv), sumar(vigentes, Venta::calcularTotal));
            }
        }
        return excel.generar();
    }

    /** Historial de un cliente: todas sus ventas, totales por moneda y lo que tiene pendiente de pago. */
    public HistorialCliente historialCliente(Long clienteId) {
        Cliente cliente = clienteService.obtenerPorId(clienteId);
        List<Venta> ventas = ventaService.buscar(null, null, null, clienteId);
        List<Venta> vigentes = ventas.stream().filter(v -> v.getEstado() == Venta.EstadoVenta.REGISTRADA).toList();
        Predicate<Venta> pendiente = v -> v.getEstadoPago() != Venta.EstadoPago.PAGADO;

        return new HistorialCliente(
                cliente.getId(), cliente.getNombreORazonSocial(), documento(cliente), cliente.getTipo().name(),
                vigentes.size(), ventas.size() - vigentes.size(),
                sumar(filtrar(vigentes, Venta.Moneda.SOLES), Venta::calcularTotal),
                sumar(filtrar(vigentes, Venta.Moneda.DOLARES), Venta::calcularTotal),
                sumar(filtrar(vigentes, Venta.Moneda.SOLES).stream().filter(pendiente).toList(), Venta::calcularTotal),
                sumar(filtrar(vigentes, Venta.Moneda.DOLARES).stream().filter(pendiente).toList(), Venta::calcularTotal),
                vigentes.stream().map(Venta::getFechaVenta).min(Comparator.naturalOrder()).orElse(null),
                vigentes.stream().map(Venta::getFechaVenta).max(Comparator.naturalOrder()).orElse(null),
                ventas.stream().map(VentaResponseDTO::desdeEntidad).toList());
    }

    public record HistorialCliente(
            Long clienteId, String nombre, String documento, String tipo,
            int cantidadVentas, int cantidadAnuladas,
            BigDecimal totalSoles, BigDecimal totalDolares,
            BigDecimal pendienteSoles, BigDecimal pendienteDolares,
            LocalDate primeraCompra, LocalDate ultimaCompra,
            List<VentaResponseDTO> ventas) {}

    private static String documento(Cliente c) {
        return c.getRuc() != null ? c.getRuc() : c.getDni();
    }

    private static List<Venta> filtrar(List<Venta> ventas, Venta.Moneda moneda) {
        return ventas.stream().filter(v -> v.getMoneda() == moneda).toList();
    }

    private static BigDecimal sumar(List<Venta> ventas, java.util.function.Function<Venta, BigDecimal> campo) {
        return ventas.stream().map(campo).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
