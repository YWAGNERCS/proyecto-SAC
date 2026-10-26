package com.bomerp.cobranza.cobro.service;

import com.bomerp.clientes.cliente.service.ClienteService;
import com.bomerp.clientes.general.integration.Cliente;
import com.bomerp.cobranza.cobro.dto.CobroRequestDTO;
import com.bomerp.cobranza.cobro.dto.CobroResponseDTO;
import com.bomerp.cobranza.cobro.dto.CuentaPorCobrarDTO;
import com.bomerp.cobranza.cobro.dto.PagadoDeVentaDTO;
import com.bomerp.cobranza.cobro.entity.Cobro;
import com.bomerp.cobranza.cobro.entity.DetalleCobro;
import com.bomerp.cobranza.cobro.repository.CobroRepository;
import com.bomerp.config.ReglaNegocioException;
import com.bomerp.facturacion.facturacion.service.FacturacionService;
import com.bomerp.ventas.venta.entity.Venta;
import com.bomerp.ventas.venta.service.VentaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cobranza: abonos (parciales o totales) a las ventas a crédito.
 * Saldo de una venta = total − suma de sus abonos. Cuando el saldo llega a 0,
 * la venta pasa a PAGADO (vía la fachada de Ventas).
 */
@Service
@Transactional
@Slf4j
public class CobranzaService {

    private final CobroRepository cobroRepository;
    private final VentaService ventaService;
    private final ClienteService clienteService;
    private final FacturacionService facturacionService;

    public CobranzaService(CobroRepository cobroRepository, VentaService ventaService,
                           ClienteService clienteService, FacturacionService facturacionService) {
        this.cobroRepository = cobroRepository;
        this.ventaService = ventaService;
        this.clienteService = clienteService;
        this.facturacionService = facturacionService;
    }

    /** Ventas con saldo pendiente (de un cliente o de todos), primero las que vencen antes. */
    @Transactional(readOnly = true)
    public List<CuentaPorCobrarDTO> porCobrar(Long clienteId) {
        if (clienteId != null) {
            clienteService.obtenerPorId(clienteId);   // 404 si no existe
        }
        List<Venta> ventas = ventaService.listarPendientesDeCobro(clienteId);
        Map<Long, BigDecimal> cobrado = cobradoPorVenta(ventas);
        LocalDate hoy = LocalDate.now();
        return ventas.stream()
                .map(v -> {
                    BigDecimal pagado = cobrado.getOrDefault(v.getId(), BigDecimal.ZERO);
                    long atraso = v.getFechaLimitePago() != null && v.getFechaLimitePago().isBefore(hoy)
                            ? ChronoUnit.DAYS.between(v.getFechaLimitePago(), hoy) : 0;
                    return new CuentaPorCobrarDTO(
                            v.getId(),
                            facturacionService.numeroComprobanteDeVenta(v.getId()).orElse(null),
                            v.getCliente().getId(),
                            v.getCliente().getNombreORazonSocial(),
                            v.getFechaVenta(),
                            v.getFechaLimitePago(),
                            atraso,
                            v.getMoneda().name(),
                            v.calcularTotal(),
                            pagado,
                            v.calcularTotal().subtract(pagado));
                })
                .filter(c -> c.saldo().signum() > 0)
                .sorted(Comparator.comparing(CuentaPorCobrarDTO::fechaLimitePago,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CobroResponseDTO> historial(Long clienteId, Long ventaId) {
        return cobroRepository.buscar(clienteId, ventaId).stream().map(CobroResponseDTO::desdeEntidad).toList();
    }

    /**
     * Registra un pago y lo reparte entre las ventas indicadas.
     *
     * Reglas: cada venta es del mismo cliente y moneda del pago, está REGISTRADA y sin pagar,
     * aparece una sola vez y el abono no supera su saldo. Fecha: hoy por defecto, nunca futura.
     */
    public CobroResponseDTO registrar(CobroRequestDTO dto) {
        Cliente cliente = clienteService.obtenerPorId(dto.clienteId());
        LocalDate fecha = dto.fecha() != null ? dto.fecha() : LocalDate.now();
        if (fecha.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La fecha del cobro no puede ser futura.");
        }

        Cobro cobro = new Cobro();
        cobro.setCliente(cliente);
        cobro.setFecha(fecha);
        cobro.setMoneda(dto.moneda());
        cobro.setMetodoPago(dto.metodoPago());
        cobro.setReferencia(vacioANulo(dto.referencia()));
        cobro.setNotas(vacioANulo(dto.notas()));

        Set<Long> vistas = new HashSet<>();
        Map<Long, BigDecimal> saldosFinales = new HashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (CobroRequestDTO.AbonoDTO abono : dto.abonos()) {
            if (!vistas.add(abono.ventaId())) {
                throw new IllegalArgumentException("La venta #" + abono.ventaId() + " está repetida en el cobro.");
            }
            Venta venta = ventaService.obtenerPorIdPublico(abono.ventaId());
            BigDecimal monto = abono.monto().setScale(2, RoundingMode.HALF_UP);
            validarAbono(venta, cliente, dto.moneda(), monto);

            BigDecimal saldo = venta.calcularTotal().subtract(cobroRepository.totalCobradoDeVenta(venta.getId()));
            if (monto.compareTo(saldo) > 0) {
                throw new ReglaNegocioException("El abono de " + monto + " supera el saldo de la venta #"
                        + venta.getId() + " (" + saldo + " " + venta.getMoneda() + ").");
            }
            cobro.agregarDetalle(new DetalleCobro(venta, monto));
            saldosFinales.put(venta.getId(), saldo.subtract(monto));
            total = total.add(monto);
        }
        cobro.setMontoTotal(total);
        Cobro guardado = cobroRepository.save(cobro);

        // Las ventas que quedaron en 0 pasan a PAGADO
        saldosFinales.forEach((ventaId, saldo) -> {
            if (saldo.signum() == 0) {
                ventaService.marcarComoPagada(ventaId);
            }
        });
        log.info("Cobro {} registrado: {} {} de {} en {} venta(s)", guardado.getId(), total, dto.moneda(),
                cliente.getNombreORazonSocial(), saldosFinales.size());
        return CobroResponseDTO.desdeEntidad(guardado);
    }

    /**
     * Lo que el cliente ya pagó de una venta: la suma de sus abonos o, si se pagó sin abonos
     * (venta al contado), su total. Es lo que se le devuelve si la venta se anula.
     */
    @Transactional(readOnly = true)
    public BigDecimal pagadoDe(Venta venta) {
        BigDecimal cobrado = cobroRepository.totalCobradoDeVenta(venta.getId());
        if (cobrado.signum() > 0) {
            return cobrado;
        }
        return venta.getEstadoPago() == Venta.EstadoPago.PAGADO ? venta.calcularTotal() : BigDecimal.ZERO;
    }

    @Transactional(readOnly = true)
    public PagadoDeVentaDTO pagadoDeVenta(Long ventaId) {
        Venta venta = ventaService.obtenerPorIdPublico(ventaId);
        return new PagadoDeVentaDTO(venta.getId(), venta.getMoneda().name(), pagadoDe(venta));
    }

    /** Saldo pendiente de una venta (fachada para Reportes). */
    @Transactional(readOnly = true)
    public BigDecimal saldoDe(Venta venta) {
        return venta.calcularTotal().subtract(cobroRepository.totalCobradoDeVenta(venta.getId()));
    }

    private static void validarAbono(Venta venta, Cliente cliente, Venta.Moneda moneda, BigDecimal monto) {
        if (venta.getEstado() == Venta.EstadoVenta.ANULADA) {
            throw new ReglaNegocioException("La venta #" + venta.getId() + " está ANULADA: no se cobra.");
        }
        if (venta.getEstadoPago() == Venta.EstadoPago.PAGADO) {
            throw new ReglaNegocioException("La venta #" + venta.getId() + " ya está PAGADA.");
        }
        if (!venta.getCliente().getId().equals(cliente.getId())) {
            throw new IllegalArgumentException("La venta #" + venta.getId() + " no es de "
                    + cliente.getNombreORazonSocial() + ".");
        }
        if (venta.getMoneda() != moneda) {
            throw new IllegalArgumentException("La venta #" + venta.getId() + " está en " + venta.getMoneda()
                    + " y el cobro en " + moneda + ": registre un cobro por cada moneda.");
        }
        if (monto.signum() <= 0) {
            throw new IllegalArgumentException("El monto abonado debe ser mayor que 0.");
        }
    }

    private Map<Long, BigDecimal> cobradoPorVenta(List<Venta> ventas) {
        Map<Long, BigDecimal> mapa = new HashMap<>();
        if (ventas.isEmpty()) {
            return mapa;
        }
        for (Object[] fila : cobroRepository.totalesCobradosPorVenta(ventas.stream().map(Venta::getId).toList())) {
            mapa.put((Long) fila[0], (BigDecimal) fila[1]);
        }
        return mapa;
    }

    private static String vacioANulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
