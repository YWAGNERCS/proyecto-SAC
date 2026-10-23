package com.bomerp.reportes.reportes.service;

import com.bomerp.proformas.proforma.entity.Proforma;
import com.bomerp.proformas.proforma.repository.ProformaRepository;
import com.bomerp.ventas.venta.entity.Venta;
import com.bomerp.ventas.venta.repository.VentaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Solo lectura; los reportes recorren detalles e ítems (LAZY), así que cada método corre en una transacción. */
@Service
@Transactional(readOnly = true)
public class ReportesService {

    private final VentaRepository ventaRepository;
    private final ProformaRepository proformaRepository;
    private final com.bomerp.cobranza.cobro.service.CobranzaService cobranzaService;

    public ReportesService(VentaRepository ventaRepository, ProformaRepository proformaRepository,
                           com.bomerp.cobranza.cobro.service.CobranzaService cobranzaService) {
        this.ventaRepository = ventaRepository;
        this.proformaRepository = proformaRepository;
        this.cobranzaService = cobranzaService;
    }

    /**
     * RF confirmado en el brief: "Ventas por periodo".
     * Si no se pasan fechas, se usa el mes calendario actual por defecto.
     */
    public Map<String, Object> obtenerResumenVentas(LocalDate desde, LocalDate hasta) {
        LocalDate inicio = desde != null ? desde : LocalDate.now().withDayOfMonth(1);
        LocalDate fin = hasta != null ? hasta : LocalDate.now();

        // Una venta ANULADA no es ingreso ni cuenta como venta del periodo
        List<Venta> ventasDelPeriodo = ventaRepository.findByEstadoAndFechaVentaBetween(
                Venta.EstadoVenta.REGISTRADA, inicio, fin);

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

        // Una venta ANULADA ya no se cobra: se excluye de pendientes y atrasadas
        List<Venta> ventasPendientesGeneral = ventaRepository.findByEstadoAndEstadoPago(
                Venta.EstadoVenta.REGISTRADA, Venta.EstadoPago.PENDIENTE);
        List<Venta> ventasAtrasadas = ventaRepository.findByEstadoAndEstadoPagoAndFechaLimitePagoBefore(
                Venta.EstadoVenta.REGISTRADA, Venta.EstadoPago.PENDIENTE, hoy);

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
        // Se reporta lo que falta cobrar: total menos los abonos ya recibidos
        reporte.put("pendientesNoVencidas", resumenSaldos(ventasPendientesNoVencidas));
        reporte.put("atrasadas", resumenSaldos(ventasAtrasadas));
        return reporte;
    }

    /** RF: "Proformas pendientes" — expuesto explícitamente como reporte */
    public Map<String, Object> obtenerReporteProformasPendientes() {
        List<Proforma> pendientes = proformaRepository.findByEstado(Proforma.EstadoProforma.PENDIENTE);

        Map<String, Object> reporte = new HashMap<>();
        reporte.put("cantidad", pendientes.size());
        reporte.put("proformas", pendientes.stream()
                .map(c -> Map.of(
                        "id", c.getId(),
                        "cliente", c.getCliente().getNombreORazonSocial(),
                        "fechaVencimiento", c.getFechaVencimiento(),
                        "total", c.calcularTotal()))
                .toList());
        return reporte;
    }

    private Map<String, Object> resumenSaldos(List<Venta> ventas) {
        BigDecimal saldoSoles = BigDecimal.ZERO;
        BigDecimal saldoDolares = BigDecimal.ZERO;
        for (Venta v : ventas) {
            BigDecimal saldo = cobranzaService.saldoDe(v);
            if (v.getMoneda() == Venta.Moneda.SOLES) {
                saldoSoles = saldoSoles.add(saldo);
            } else {
                saldoDolares = saldoDolares.add(saldo);
            }
        }
        Map<String, Object> resumen = new HashMap<>();
        resumen.put("cantidad", ventas.size());
        resumen.put("totalSoles", saldoSoles);
        resumen.put("totalDolares", saldoDolares);
        return resumen;
    }

}
