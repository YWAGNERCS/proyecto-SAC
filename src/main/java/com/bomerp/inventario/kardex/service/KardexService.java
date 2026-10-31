package com.bomerp.inventario.kardex.service;

import com.bomerp.inventario.ajuste.entity.AjusteInventario;
import com.bomerp.inventario.ajuste.service.AjusteInventarioService;
import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.catalogo.producto.service.ProductoService;
import com.bomerp.compras.compra.entity.Compra;
import com.bomerp.compras.compra.service.CompraService;
import com.bomerp.compras.detallecompra.entity.DetalleCompra;
import com.bomerp.config.exportacion.ExcelBuilder;
import com.bomerp.servicios.orden.entity.MaterialOrden;
import com.bomerp.servicios.orden.entity.OrdenTrabajo;
import com.bomerp.servicios.orden.service.OrdenTrabajoService;
import com.bomerp.ventas.detalleventa.entity.DetalleVenta;
import com.bomerp.ventas.venta.entity.Venta;
import com.bomerp.ventas.venta.service.VentaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Kardex (historial de movimientos de inventario) de un producto:
 *   ENTRADA    → compra a proveedor (suma stock)
 *   SALIDA     → venta, o material usado en una orden de trabajo (resta stock)
 *   DEVOLUCIÓN → anulación de una venta (devuelve el stock)
 *   AJUSTE_ENTRADA / AJUSTE_SALIDA → ajuste de inventario (conteo físico, merma, dañado...)
 *
 * El saldo solo aplica a productos que controlan stock (materiales). Para los equipos
 * se listan los movimientos sin saldo, porque no se almacenan.
 * El saldo inicial se reconstruye desde el stock actual: inicial = actual − entradas + salidas − devoluciones.
 */
@Service
@Transactional(readOnly = true)
public class KardexService {

    public enum TipoMovimiento {
        ENTRADA, SALIDA, DEVOLUCION, AJUSTE_ENTRADA, AJUSTE_SALIDA;

        /** Resta stock. */
        public boolean resta() {
            return this == SALIDA || this == AJUSTE_SALIDA;
        }
    }

    private final ProductoService productoService;
    private final CompraService compraService;
    private final VentaService ventaService;
    private final OrdenTrabajoService ordenTrabajoService;
    private final AjusteInventarioService ajusteService;

    public KardexService(ProductoService productoService, CompraService compraService, VentaService ventaService,
                         OrdenTrabajoService ordenTrabajoService, AjusteInventarioService ajusteService) {
        this.ajusteService = ajusteService;
        this.productoService = productoService;
        this.compraService = compraService;
        this.ventaService = ventaService;
        this.ordenTrabajoService = ordenTrabajoService;
    }

    public Kardex kardex(Long productoId, LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new IllegalArgumentException("La fecha 'desde' no puede ser posterior a 'hasta'.");
        }
        Producto producto = productoService.obtenerPorId(productoId);   // 404 si no existe
        boolean controlaStock = producto.controlaStock();

        List<Movimiento> todos = new ArrayList<>();
        for (DetalleCompra d : compraService.detallesDeProducto(productoId)) {
            Compra c = d.getCompra();
            todos.add(new Movimiento(c.getFechaCompra(), TipoMovimiento.ENTRADA,
                    "Compra #" + c.getId() + (c.getNumeroFactura() != null ? " · Fact. " + c.getNumeroFactura() : ""),
                    c.getProveedor().getNombreORazonSocial(), d.getCantidad(), d.getPrecioUnitarioCompra(),
                    c.getMoneda().name(), null));
        }
        for (DetalleVenta d : ventaService.detallesDeProducto(productoId)) {
            Venta v = d.getVenta();
            todos.add(new Movimiento(v.getFechaVenta(), TipoMovimiento.SALIDA, "Venta #" + v.getId(),
                    v.getCliente().getNombreORazonSocial(), d.getCantidad(), d.getPrecioUnitario(), v.getMoneda().name(), null));
            if (v.getEstado() == Venta.EstadoVenta.ANULADA) {
                todos.add(new Movimiento(v.getFechaAnulacion() != null ? v.getFechaAnulacion() : v.getFechaVenta(),
                        TipoMovimiento.DEVOLUCION, "Anulación de venta #" + v.getId(),
                        v.getCliente().getNombreORazonSocial(), d.getCantidad(), d.getPrecioUnitario(), v.getMoneda().name(), null));
            }
        }
        for (MaterialOrden m : ordenTrabajoService.materialesUsadosDeProducto(productoId)) {
            OrdenTrabajo o = m.getOrden();
            todos.add(new Movimiento(o.getFechaRealizada(), TipoMovimiento.SALIDA, "Orden de trabajo #" + o.getId(),
                    o.getCliente().getNombreORazonSocial(), m.getCantidad(), m.getCostoUnitario(), m.getMoneda(), null));
        }
        for (AjusteInventario a : ajusteService.deProducto(productoId)) {
            todos.add(new Movimiento(a.getFecha(),
                    a.getTipo() == AjusteInventario.Tipo.ENTRADA ? TipoMovimiento.AJUSTE_ENTRADA : TipoMovimiento.AJUSTE_SALIDA,
                    "Ajuste #" + a.getId() + " · " + a.getMotivo().getNombre(),
                    a.getRegistradoPor() != null ? a.getRegistradoPor() : "—", a.getCantidad(), null, null, null));
        }
        // Orden cronológico; el mismo día: entradas, salidas, devoluciones y al final los ajustes
        todos.sort(Comparator.comparing(Movimiento::fecha).thenComparing(Movimiento::tipo));

        Integer stockActual = producto.getStockActual() != null ? producto.getStockActual() : 0;
        Integer saldo = null;
        if (controlaStock) {
            int neto = todos.stream().mapToInt(m -> m.tipo().resta() ? -m.cantidad() : m.cantidad()).sum();
            saldo = stockActual - neto;
        }

        Integer saldoInicialPeriodo = saldo;
        List<Movimiento> delPeriodo = new ArrayList<>();
        int entradas = 0, salidas = 0, devoluciones = 0, ajusteEntradas = 0, ajusteSalidas = 0;
        for (Movimiento m : todos) {
            if (saldo != null) {
                saldo += m.tipo().resta() ? -m.cantidad() : m.cantidad();
            }
            boolean antes = desde != null && m.fecha().isBefore(desde);
            boolean despues = hasta != null && m.fecha().isAfter(hasta);
            if (antes) {
                saldoInicialPeriodo = saldo;
                continue;
            }
            if (despues) continue;
            delPeriodo.add(new Movimiento(m.fecha(), m.tipo(), m.documento(), m.tercero(), m.cantidad(),
                    m.precioUnitario(), m.moneda(), saldo));
            switch (m.tipo()) {
                case ENTRADA -> entradas += m.cantidad();
                case SALIDA -> salidas += m.cantidad();
                case DEVOLUCION -> devoluciones += m.cantidad();
                case AJUSTE_ENTRADA -> ajusteEntradas += m.cantidad();
                case AJUSTE_SALIDA -> ajusteSalidas += m.cantidad();
            }
        }

        return new Kardex(producto.getId(), producto.getNombre(),
                producto.getCategoria() != null ? producto.getCategoria().getNombre() : null,
                producto.getSector().name(), controlaStock, controlaStock ? stockActual : null,
                producto.getStockMinimo(), saldoInicialPeriodo, entradas, salidas, devoluciones, delPeriodo,
                ajusteEntradas, ajusteSalidas);
    }

    public byte[] exportarExcel(Long productoId, LocalDate desde, LocalDate hasta) {
        Kardex k = kardex(productoId, desde, hasta);
        ExcelBuilder excel = new ExcelBuilder();
        ExcelBuilder.Hoja hoja = excel.hoja("Kardex", "Kardex: " + k.producto(),
                ExcelBuilder.describirPeriodo(desde, hasta)
                        + (k.categoria() != null ? " · " + k.categoria() : "")
                        + (k.controlaStock() ? " · saldo inicial " + k.saldoInicial() : " · equipo: no controla stock"),
                ExcelBuilder.fecha("Fecha"), ExcelBuilder.texto("Movimiento"), ExcelBuilder.texto("Documento"),
                ExcelBuilder.texto("Proveedor / Cliente"), ExcelBuilder.entero("Entrada"), ExcelBuilder.entero("Salida"),
                ExcelBuilder.entero("Saldo"), ExcelBuilder.moneda("Precio unitario"), ExcelBuilder.texto("Moneda"));
        for (Movimiento m : k.movimientos()) {
            boolean sale = m.tipo().resta();
            String tipo = switch (m.tipo()) {
                case DEVOLUCION -> "DEVOLUCIÓN";
                case AJUSTE_ENTRADA -> "AJUSTE (entrada)";
                case AJUSTE_SALIDA -> "AJUSTE (salida)";
                default -> m.tipo().name();
            };
            hoja.fila(m.fecha(), tipo, m.documento(),
                    m.tercero(), sale ? null : m.cantidad(), sale ? m.cantidad() : null, m.saldo(),
                    m.precioUnitario(), m.moneda());
        }
        Integer saldoFinal = k.movimientos().isEmpty() ? k.saldoInicial()
                : k.movimientos().get(k.movimientos().size() - 1).saldo();
        hoja.filaTotal("Totales del periodo", null, null, null, k.totalEntradas() + k.totalDevoluciones() + k.totalAjusteEntradas(),
                k.totalSalidas() + k.totalAjusteSalidas(), saldoFinal);
        return excel.generar();
    }

    public record Movimiento(LocalDate fecha, TipoMovimiento tipo, String documento, String tercero,
                             int cantidad, BigDecimal precioUnitario, String moneda, Integer saldo) {}

    public record Kardex(Long productoId, String producto, String categoria, String sector, boolean controlaStock,
                         Integer stockActual, Integer stockMinimo, Integer saldoInicial,
                         int totalEntradas, int totalSalidas, int totalDevoluciones,
                         List<Movimiento> movimientos, int totalAjusteEntradas, int totalAjusteSalidas) {}
}
