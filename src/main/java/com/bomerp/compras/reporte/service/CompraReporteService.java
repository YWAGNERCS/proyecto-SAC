package com.bomerp.compras.reporte.service;

import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.compras.compra.dto.CompraResponseDTO;
import com.bomerp.compras.compra.entity.Compra;
import com.bomerp.compras.compra.service.CompraService;
import com.bomerp.compras.detallecompra.entity.DetalleCompra;
import com.bomerp.config.exportacion.ExcelBuilder;
import com.bomerp.proveedores.proveedor.entity.Proveedor;
import com.bomerp.proveedores.proveedor.service.ProveedorService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Reportes del módulo Compras: exportación a Excel e historial por proveedor.
 * Usa solo fachadas públicas (CompraService, ProveedorService).
 */
@Service
@Transactional(readOnly = true)
public class CompraReporteService {

    private final CompraService compraService;
    private final ProveedorService proveedorService;

    public CompraReporteService(CompraService compraService, ProveedorService proveedorService) {
        this.compraService = compraService;
        this.proveedorService = proveedorService;
    }

    /** Excel con dos hojas: "Compras" (una fila por compra) y "Detalle" (una fila por producto comprado). */
    public byte[] exportarExcel(LocalDate desde, LocalDate hasta, Long proveedorId) {
        List<Compra> compras = compraService.buscar(desde, hasta, proveedorId);
        String filtros = ExcelBuilder.describirPeriodo(desde, hasta)
                + (proveedorId != null ? " · proveedor " + proveedorService.obtenerPorId(proveedorId).getNombreORazonSocial() : "");

        ExcelBuilder excel = new ExcelBuilder();
        ExcelBuilder.Hoja hojaCompras = excel.hoja("Compras", "Reporte de compras a proveedores", filtros,
                ExcelBuilder.entero("N° compra"), ExcelBuilder.fecha("Fecha"), ExcelBuilder.texto("Proveedor"),
                ExcelBuilder.texto("RUC / DNI"), ExcelBuilder.texto("Factura del proveedor"), ExcelBuilder.entero("Líneas"),
                ExcelBuilder.entero("Unidades"), ExcelBuilder.texto("Moneda"), ExcelBuilder.moneda("Total"));
        ExcelBuilder.Hoja hojaDetalle = excel.hoja("Detalle", "Detalle de productos comprados", filtros,
                ExcelBuilder.entero("N° compra"), ExcelBuilder.fecha("Fecha"), ExcelBuilder.texto("Proveedor"),
                ExcelBuilder.texto("Factura del proveedor"), ExcelBuilder.texto("Producto"), ExcelBuilder.texto("Categoría"),
                ExcelBuilder.texto("Sector"), ExcelBuilder.entero("Cantidad"), ExcelBuilder.moneda("Precio unitario"),
                ExcelBuilder.moneda("Subtotal"), ExcelBuilder.texto("Moneda"));

        for (Compra c : compras) {
            hojaCompras.fila(c.getId(), c.getFechaCompra(), c.getProveedor().getNombreORazonSocial(),
                    documento(c.getProveedor()), c.getNumeroFactura(), c.getDetalles().size(),
                    c.getDetalles().stream().mapToInt(DetalleCompra::getCantidad).sum(), c.getMoneda(), c.getTotal());
            for (DetalleCompra d : c.getDetalles()) {
                hojaDetalle.fila(c.getId(), c.getFechaCompra(), c.getProveedor().getNombreORazonSocial(),
                        c.getNumeroFactura(), d.getProducto().getNombre(),
                        d.getProducto().getCategoria() != null ? d.getProducto().getCategoria().getNombre() : null,
                        d.getProducto().getSector(), d.getCantidad(), d.getPrecioUnitarioCompra(),
                        subtotal(d), c.getMoneda());
            }
        }
        for (Producto.Moneda moneda : Producto.Moneda.values()) {
            List<Compra> deMoneda = compras.stream().filter(c -> c.getMoneda() == moneda).toList();
            if (!deMoneda.isEmpty()) {
                hojaCompras.filaTotal("Total (" + deMoneda.size() + " compras)", null, null, null, null, null, null,
                        moneda, sumar(deMoneda));
            }
        }
        return excel.generar();
    }

    /** Historial de un proveedor: todas sus compras y el monto comprado por moneda. */
    public HistorialProveedor historialProveedor(Long proveedorId) {
        Proveedor proveedor = proveedorService.obtenerPorId(proveedorId);
        List<Compra> compras = compraService.buscar(null, null, proveedorId);
        return new HistorialProveedor(
                proveedor.getId(), proveedor.getNombreORazonSocial(), documento(proveedor), proveedor.getTipo().name(),
                compras.size(),
                sumar(compras.stream().filter(c -> c.getMoneda() == Producto.Moneda.SOLES).toList()),
                sumar(compras.stream().filter(c -> c.getMoneda() == Producto.Moneda.DOLARES).toList()),
                compras.stream().map(Compra::getFechaCompra).min(Comparator.naturalOrder()).orElse(null),
                compras.stream().map(Compra::getFechaCompra).max(Comparator.naturalOrder()).orElse(null),
                compras.stream().map(CompraResponseDTO::desdeEntidad).toList());
    }

    public record HistorialProveedor(
            Long proveedorId, String nombre, String documento, String tipo,
            int cantidadCompras, BigDecimal totalSoles, BigDecimal totalDolares,
            LocalDate primeraCompra, LocalDate ultimaCompra,
            List<CompraResponseDTO> compras) {}

    private static String documento(Proveedor p) {
        return p.getRuc() != null ? p.getRuc() : p.getDni();
    }

    private static BigDecimal subtotal(DetalleCompra d) {
        return d.getPrecioUnitarioCompra() == null ? BigDecimal.ZERO
                : d.getPrecioUnitarioCompra().multiply(BigDecimal.valueOf(d.getCantidad()));
    }

    private static BigDecimal sumar(List<Compra> compras) {
        return compras.stream().map(c -> c.getTotal() != null ? c.getTotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
