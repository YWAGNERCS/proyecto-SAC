package com.jdrefrigeracion.reportes.services;

import com.jdrefrigeracion.cotizaciones.models.Cotizacion;
import com.jdrefrigeracion.cotizaciones.repositories.CotizacionRepository;
import com.jdrefrigeracion.ventas.models.Venta;
import com.jdrefrigeracion.ventas.repositories.VentaRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportesService {

    private final VentaRepository ventaRepository;
    private final CotizacionRepository cotizacionRepository;

    public ReportesService(VentaRepository ventaRepository, CotizacionRepository cotizacionRepository) {
        this.ventaRepository = ventaRepository;
        this.cotizacionRepository = cotizacionRepository;
    }

    /**
     * RF confirmado en el brief: "Ventas por periodo".
     * Si no se pasan fechas, se usa el mes calendario actual por defecto.
     */
    public Map<String, Object> obtenerResumenVentas(LocalDate desde, LocalDate hasta) {
        LocalDate inicio = desde != null ? desde : LocalDate.now().withDayOfMonth(1);
        LocalDate fin = hasta != null ? hasta : LocalDate.now();

        List<Venta> ventasDelPeriodo = ventaRepository.findByFechaVentaBetween(inicio, fin);

        long cantidadVentas = ventasDelPeriodo.size();
        BigDecimal totalIngresosSoles = BigDecimal.ZERO;
        BigDecimal totalIngresosDolares = BigDecimal.ZERO;

        for (Venta v : ventasDelPeriodo) {
            // Solo las ventas efectivamente PAGADAS cuentan como ingreso real.
            if (v.getEstadoPago() == Venta.EstadoPago.PAGADO) {
                if (v.getMoneda() == Venta.Moneda.SOLES) {
                    totalIngresosSoles = totalIngresosSoles.add(v.calcularTotal());
                } else {
                    totalIngresosDolares = totalIngresosDolares.add(v.calcularTotal());
                }
            }
        }

        Map<String, Object> resumen = new HashMap<>();
        resumen.put("periodoDesde", inicio);
        resumen.put("periodoHasta", fin);
        resumen.put("cantidadTotalVentas", cantidadVentas);
        resumen.put("totalIngresosSoles", totalIngresosSoles);
        resumen.put("totalIngresosDolares", totalIngresosDolares);
        return resumen;
    }

    /**
     * RF: "Pagos atrasados" — antes se confundía con "pendientes".
     * Ahora se separan claramente:
     *   - Pendientes: aún no vencen (fechaLimitePago >= hoy)
     *   - Atrasados: ya pasó su fecha límite de pago y siguen sin pagarse
     */
    public Map<String, Object> obtenerReporteCobranza() {
        LocalDate hoy = LocalDate.now();

        List<Venta> ventasPendientesGeneral = ventaRepository.findByEstadoPago(Venta.EstadoPago.PENDIENTE);
        List<Venta> ventasAtrasadas = ventaRepository
                .findByEstadoPagoAndFechaLimitePagoBefore(Venta.EstadoPago.PENDIENTE, hoy);

        // Comparamos por ID, no por igualdad de objeto: dos consultas separadas
        // pueden devolver instancias distintas de Java para la misma fila,
        // y Venta no sobreescribe equals()/hashCode().
        java.util.Set<Long> idsAtrasadas = ventasAtrasadas.stream()
                .map(Venta::getId)
                .collect(java.util.stream.Collectors.toSet());

        List<Venta> ventasPendientesNoVencidas = ventasPendientesGeneral.stream()
                .filter(v -> !idsAtrasadas.contains(v.getId()))
                .toList();

        Map<String, Object> reporte = new HashMap<>();
        reporte.put("pendientesNoVencidas", resumenMontos(ventasPendientesNoVencidas));
        reporte.put("atrasadas", resumenMontos(ventasAtrasadas));
        return reporte;
    }

    /** RF: "Cotizaciones pendientes" — expuesto explícitamente como reporte */
    public Map<String, Object> obtenerReporteCotizacionesPendientes() {
        List<Cotizacion> pendientes = cotizacionRepository.findByEstado(Cotizacion.EstadoCotizacion.PENDIENTE);

        Map<String, Object> reporte = new HashMap<>();
        reporte.put("cantidad", pendientes.size());
        reporte.put("cotizaciones", pendientes.stream()
                .map(c -> Map.of(
                        "id", c.getId(),
                        "cliente", c.getCliente().getNombreORazonSocial(),
                        "fechaVencimiento", c.getFechaVencimiento(),
                        "total", c.calcularTotal()))
                .toList());
        return reporte;
    }

    private Map<String, Object> resumenMontos(List<Venta> ventas) {
        BigDecimal totalSoles = BigDecimal.ZERO;
        BigDecimal totalDolares = BigDecimal.ZERO;
        for (Venta v : ventas) {
            if (v.getMoneda() == Venta.Moneda.SOLES) {
                totalSoles = totalSoles.add(v.calcularTotal());
            } else {
                totalDolares = totalDolares.add(v.calcularTotal());
            }
        }
        Map<String, Object> resumen = new HashMap<>();
        resumen.put("cantidad", ventas.size());
        resumen.put("totalSoles", totalSoles);
        resumen.put("totalDolares", totalDolares);
        return resumen;
    }
}
