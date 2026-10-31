package com.bomerp.reportes.rentabilidad;

import com.bomerp.config.tipocambio.TipoCambioService;
import com.bomerp.servicios.orden.entity.MaterialOrden;
import com.bomerp.servicios.orden.entity.OrdenTrabajo;
import com.bomerp.servicios.orden.service.OrdenTrabajoService;
import com.bomerp.ventas.detalleventa.entity.DetalleVenta;
import com.bomerp.ventas.venta.entity.Venta;
import com.bomerp.ventas.venta.service.VentaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;

/**
 * Rentabilidad: cuánto se ganó de verdad (precio de venta sin IGV, con descuento, menos el costo del
 * producto al momento de vender) por venta, cliente, producto, categoría y técnico.
 *
 * Todo en soles: lo vendido en dólares se convierte con el tipo de cambio de cada venta. Una línea sin
 * costo (venta en otra moneda sin tipo de cambio, producto sin costo) no se suma y se cuenta aparte.
 * Por técnico: órdenes REALIZADAS en el periodo; ingreso = la venta que las facturó; costo = su mano
 * de obra más los materiales que usó la orden.
 * Las ventas anteriores al 31/10/2026 usan el costo del producto de esa fecha (no se guardaba antes).
 */
@Service
@Transactional(readOnly = true)
public class RentabilidadService {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private final VentaService ventaService;
    private final OrdenTrabajoService ordenService;
    private final TipoCambioService tipoCambioService;

    public RentabilidadService(VentaService ventaService, OrdenTrabajoService ordenService,
                               TipoCambioService tipoCambioService) {
        this.ventaService = ventaService;
        this.ordenService = ordenService;
        this.tipoCambioService = tipoCambioService;
    }

    /** Una fila agrupada: ingresos y costos en soles, margen y margen % sobre el ingreso. */
    public record Fila(String clave, String nombre, String detalle, int cantidad, BigDecimal ingresos,
                       BigDecimal costos, BigDecimal margen, BigDecimal margenPorcentaje) {}

    public record VentaFila(Long ventaId, LocalDate fecha, String cliente, String vendedor, String moneda,
                            BigDecimal ingresos, BigDecimal costos, BigDecimal margen, BigDecimal margenPorcentaje,
                            boolean incompleta) {}

    public record TecnicoFila(String tecnico, int ordenes, int facturadas, BigDecimal ingresos, BigDecimal costoManoObra,
                              BigDecimal costoMateriales, BigDecimal margen, BigDecimal margenPorcentaje) {}

    public record Resumen(int ventas, BigDecimal ingresos, BigDecimal costos, BigDecimal margen,
                          BigDecimal margenPorcentaje, int lineasSinCosto) {}

    public record Rentabilidad(LocalDate desde, LocalDate hasta, Resumen resumen, List<VentaFila> porVenta,
                               List<Fila> porCliente, List<Fila> porProducto, List<Fila> porCategoria,
                               List<TecnicoFila> porTecnico) {}

    /** Acumulador de ingreso y costo. */
    private static final class Suma {
        String nombre;
        String detalle;
        int cantidad;
        BigDecimal ingresos = BigDecimal.ZERO;
        BigDecimal costos = BigDecimal.ZERO;

        Fila fila(String clave) {
            BigDecimal margen = ingresos.subtract(costos);
            return new Fila(clave, nombre, detalle, cantidad, ingresos, costos, margen, porcentaje(margen, ingresos));
        }
    }

    public Rentabilidad calcular(LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new IllegalArgumentException("La fecha 'desde' no puede ser posterior a 'hasta'.");
        }
        List<DetalleVenta> lineas = ventaService.lineasVendidas(desde, hasta);

        Map<Long, Suma> porVenta = new LinkedHashMap<>();
        Map<Long, Boolean> incompleta = new HashMap<>();
        Map<String, Suma> porCliente = new HashMap<>();
        Map<String, Suma> porProducto = new HashMap<>();
        Map<String, Suma> porCategoria = new HashMap<>();
        int sinCosto = 0;

        for (DetalleVenta d : lineas) {
            Venta v = d.getVenta();
            Suma sv = porVenta.computeIfAbsent(v.getId(), k -> new Suma());
            BigDecimal ingreso = aSoles(d.calcularSubtotalDetalle(), v);
            BigDecimal costo = d.getCostoUnitario() == null ? null
                    : aSoles(d.getCostoUnitario().multiply(BigDecimal.valueOf(d.getCantidad())), v);
            if (ingreso == null || costo == null) {
                sinCosto++;
                incompleta.put(v.getId(), true);
                continue;
            }
            sumar(sv, v.getCliente().getNombreORazonSocial(), null, 0, ingreso, costo);
            sumar(porCliente.computeIfAbsent("C" + v.getCliente().getId(), k -> new Suma()),
                    v.getCliente().getNombreORazonSocial(), v.getCliente().getRuc() != null ? v.getCliente().getRuc() : v.getCliente().getDni(), 0, ingreso, costo);
            sumar(porProducto.computeIfAbsent("P" + d.getProducto().getId(), k -> new Suma()),
                    d.getProducto().getNombre(), d.getProducto().getSector().name(), d.getCantidad(), ingreso, costo);
            String categoria = d.getProducto().getCategoria() != null ? d.getProducto().getCategoria().getNombre() : "Sin categoría";
            sumar(porCategoria.computeIfAbsent(categoria, k -> new Suma()), categoria, null, d.getCantidad(), ingreso, costo);
        }
        // Ventas por cliente: cuántas ventas distintas tiene cada uno
        Map<String, Set<Long>> ventasDeCliente = new HashMap<>();
        lineas.forEach(d -> ventasDeCliente.computeIfAbsent("C" + d.getVenta().getCliente().getId(), k -> new HashSet<>())
                .add(d.getVenta().getId()));
        porCliente.forEach((k, s) -> s.cantidad = ventasDeCliente.getOrDefault(k, Set.of()).size());

        Map<Long, Venta> ventas = new HashMap<>();
        lineas.forEach(d -> ventas.putIfAbsent(d.getVenta().getId(), d.getVenta()));
        List<VentaFila> filasVenta = porVenta.entrySet().stream().map(e -> {
                    Venta v = ventas.get(e.getKey());
                    Suma s = e.getValue();
                    BigDecimal margen = s.ingresos.subtract(s.costos);
                    return new VentaFila(v.getId(), v.getFechaVenta(), v.getCliente().getNombreORazonSocial(),
                            v.getVendedorNombre(), v.getMoneda().name(), s.ingresos, s.costos, margen,
                            porcentaje(margen, s.ingresos), incompleta.getOrDefault(v.getId(), false));
                })
                .sorted(Comparator.comparing(VentaFila::fecha).reversed().thenComparing(VentaFila::ventaId, Comparator.reverseOrder()))
                .toList();

        BigDecimal ingresos = sumaDe(porVenta.values(), s -> s.ingresos);
        BigDecimal costos = sumaDe(porVenta.values(), s -> s.costos);
        BigDecimal margen = ingresos.subtract(costos);
        Resumen resumen = new Resumen(porVenta.size(), ingresos, costos, margen, porcentaje(margen, ingresos), sinCosto);

        return new Rentabilidad(desde, hasta, resumen, filasVenta, ordenar(porCliente), ordenar(porProducto),
                ordenar(porCategoria), porTecnico(desde, hasta));
    }

    private List<TecnicoFila> porTecnico(LocalDate desde, LocalDate hasta) {
        List<OrdenTrabajo> ordenes = ordenService.realizadasEntre(desde, hasta);
        Set<Long> idsVentas = new HashSet<>();
        ordenes.forEach(o -> {
            if (o.getVentaFacturadaId() != null) idsVentas.add(o.getVentaFacturadaId());
        });
        Map<Long, List<DetalleVenta>> lineasDeVenta = new HashMap<>();
        if (!idsVentas.isEmpty()) {
            for (DetalleVenta d : ventaService.lineasDeVentas(idsVentas)) {
                lineasDeVenta.computeIfAbsent(d.getVenta().getId(), k -> new ArrayList<>()).add(d);
            }
        }
        BigDecimal tcHoy = null;
        boolean tcConsultado = false;

        Map<String, TecnicoAcum> acum = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (OrdenTrabajo o : ordenes) {
            TecnicoAcum t = acum.computeIfAbsent(o.getTecnico().trim(), k -> new TecnicoAcum());
            t.ordenes++;
            BigDecimal tcVenta = null;
            List<DetalleVenta> factura = o.getVentaFacturadaId() != null ? lineasDeVenta.get(o.getVentaFacturadaId()) : null;
            if (factura != null) {
                t.facturadas++;
                for (DetalleVenta d : factura) {
                    Venta v = d.getVenta();
                    tcVenta = v.getTipoCambio();
                    BigDecimal ingreso = aSoles(d.calcularSubtotalDetalle(), v);
                    BigDecimal costo = d.getCostoUnitario() == null ? BigDecimal.ZERO
                            : aSoles(d.getCostoUnitario().multiply(BigDecimal.valueOf(d.getCantidad())), v);
                    t.ingresos = t.ingresos.add(ingreso == null ? BigDecimal.ZERO : ingreso);
                    t.manoObra = t.manoObra.add(costo == null ? BigDecimal.ZERO : costo);
                }
            }
            for (MaterialOrden m : o.getMateriales()) {
                if (m.getCostoUnitario() == null) continue;
                BigDecimal costo = m.getCostoUnitario().multiply(BigDecimal.valueOf(m.getCantidad()));
                if ("DOLARES".equals(m.getMoneda())) {
                    BigDecimal tc = tcVenta;
                    if (tc == null) {
                        if (!tcConsultado) {
                            tcHoy = tipoCambioService.resolverSiSePuede(null);
                            tcConsultado = true;
                        }
                        tc = tcHoy;
                    }
                    if (tc == null) continue;
                    costo = costo.multiply(tc);
                }
                t.materiales = t.materiales.add(costo.setScale(2, RoundingMode.HALF_UP));
            }
        }
        return acum.entrySet().stream().map(e -> {
                    TecnicoAcum t = e.getValue();
                    BigDecimal margen = t.ingresos.subtract(t.manoObra).subtract(t.materiales);
                    return new TecnicoFila(e.getKey(), t.ordenes, t.facturadas, t.ingresos, t.manoObra, t.materiales,
                            margen, porcentaje(margen, t.ingresos));
                })
                .sorted(Comparator.comparing(TecnicoFila::margen).reversed())
                .toList();
    }

    private static final class TecnicoAcum {
        int ordenes;
        int facturadas;
        BigDecimal ingresos = BigDecimal.ZERO;
        BigDecimal manoObra = BigDecimal.ZERO;
        BigDecimal materiales = BigDecimal.ZERO;
    }

    // ── Ayudas ──────────────────────────────────────────────

    private static void sumar(Suma s, String nombre, String detalle, int cantidad, BigDecimal ingreso, BigDecimal costo) {
        s.nombre = nombre;
        s.detalle = detalle;
        s.cantidad += cantidad;
        s.ingresos = s.ingresos.add(ingreso);
        s.costos = s.costos.add(costo);
    }

    /** Monto de la venta en soles; null si es en dólares sin tipo de cambio. */
    private static BigDecimal aSoles(BigDecimal monto, Venta v) {
        if (v.getMoneda() == Venta.Moneda.SOLES) return monto.setScale(2, RoundingMode.HALF_UP);
        return v.getTipoCambio() == null ? null : monto.multiply(v.getTipoCambio()).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal porcentaje(BigDecimal margen, BigDecimal ingresos) {
        return ingresos.signum() == 0 ? BigDecimal.ZERO
                : margen.multiply(CIEN).divide(ingresos, 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal sumaDe(Collection<Suma> sumas, Function<Suma, BigDecimal> campo) {
        return sumas.stream().map(campo).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Mayor margen primero. */
    private static List<Fila> ordenar(Map<String, Suma> grupo) {
        return grupo.entrySet().stream().map(e -> e.getValue().fila(e.getKey()))
                .sorted(Comparator.comparing(Fila::margen).reversed())
                .toList();
    }
}
