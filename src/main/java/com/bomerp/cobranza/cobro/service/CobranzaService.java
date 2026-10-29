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
import com.bomerp.config.RecursoNoEncontradoException;
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
        Map<Long, BigDecimal> deducido = deduccionesPorVenta(ventas);
        LocalDate hoy = LocalDate.now();
        return ventas.stream()
                .map(v -> {
                    BigDecimal pagado = cobrado.getOrDefault(v.getId(), BigDecimal.ZERO);
                    long atraso = v.getFechaLimitePago() != null && v.getFechaLimitePago().isBefore(hoy)
                            ? ChronoUnit.DAYS.between(v.getFechaLimitePago(), hoy) : 0;
                    BigDecimal saldo = v.calcularTotal().subtract(pagado);
                    BigDecimal deduccionPendiente = deduccionPendiente(v, deducido.getOrDefault(v.getId(), BigDecimal.ZERO));
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
                            saldo,
                            v.getTipoDeduccion().name(),
                            v.getMontoDeduccion(),
                            deduccionPendiente,
                            saldo.subtract(deduccionPendiente).max(BigDecimal.ZERO));
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
     * aparece una sola vez y el abono no supera su saldo. Fecha: hoy por defecto, nunca futura
     * ni anterior a la venta. El mismo N° de operación (con el mismo método) no se registra dos veces.
     *
     * @param usuario preferred_username del token de quien registra (no viene del request)
     */
    public CobroResponseDTO registrar(CobroRequestDTO dto, String usuario) {
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
        cobro.setRegistradoPor(usuario);
        if (dto.metodoPago().esDeduccion() && cobro.getReferencia() == null) {
            throw new IllegalArgumentException(dto.metodoPago() == Cobro.MetodoPago.DETRACCION
                    ? "Indique el N° de la constancia de depósito de la detracción."
                    : "Indique el N° del comprobante de retención.");
        }

        // El mismo voucher (método + N° de operación) no entra dos veces
        if (cobro.getReferencia() != null) {
            cobroRepository.vigentesConReferencia(dto.metodoPago(), cobro.getReferencia()).stream().findFirst()
                    .ifPresent(previo -> {
                        throw new ReglaNegocioException("El N° de operación " + cobro.getReferencia() + " ("
                                + dto.metodoPago() + ") ya se registró en el cobro #" + previo.getId() + " del "
                                + previo.getFecha() + ". Si es otro pago, revise el número; si el anterior estaba mal, anúlelo primero.");
                    });
        }

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
            if (dto.metodoPago().esDeduccion()) {
                validarDeduccion(venta, dto.metodoPago(), monto);
            }
            if (fecha.isBefore(venta.getFechaVenta())) {
                throw new IllegalArgumentException("La fecha del cobro (" + fecha + ") es anterior a la venta #"
                        + venta.getId() + " (" + venta.getFechaVenta() + ").");
            }

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
     * Anula un cobro mal registrado (monto o venta equivocados, pago duplicado, cheque rechazado).
     *
     * El cobro no se borra: queda ANULADO con fecha, motivo y usuario, y deja de contar en el
     * saldo de sus ventas; las que estaban PAGADAS por él vuelven a PENDIENTE. No se anula un cobro
     * de una venta ya anulada: ese dinero se resuelve en Devoluciones.
     *
     * @param usuario preferred_username del token de quien anula
     */
    public CobroResponseDTO anular(Long cobroId, String motivo, String usuario) {
        Cobro cobro = cobroRepository.findById(cobroId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el cobro #" + cobroId));
        if (cobro.getEstado() == Cobro.Estado.ANULADO) {
            throw new ReglaNegocioException("El cobro #" + cobroId + " ya fue anulado el " + cobro.getFechaAnulacion() + ".");
        }
        String motivoLimpio = vacioANulo(motivo);
        if (motivoLimpio == null || motivoLimpio.length() < 5) {
            throw new IllegalArgumentException("Indique el motivo de la anulación (al menos 5 caracteres).");
        }
        for (DetalleCobro detalle : cobro.getDetalles()) {
            if (detalle.getVenta().getEstado() == Venta.EstadoVenta.ANULADA) {
                throw new ReglaNegocioException("El cobro #" + cobroId + " abonó a la venta #" + detalle.getVenta().getId()
                        + ", que ya está ANULADA: ese dinero se resuelve en Cobranza › Devoluciones.");
            }
        }

        cobro.setEstado(Cobro.Estado.ANULADO);
        cobro.setFechaAnulacion(LocalDate.now());
        cobro.setMotivoAnulacion(motivoLimpio);
        cobro.setAnuladoPor(usuario);
        Cobro guardado = cobroRepository.save(cobro);

        // Sin este abono, ninguna de sus ventas queda cubierta: las que estaban PAGADAS vuelven a PENDIENTE
        for (DetalleCobro detalle : guardado.getDetalles()) {
            if (detalle.getVenta().getEstadoPago() == Venta.EstadoPago.PAGADO) {
                ventaService.volverAPendiente(detalle.getVenta().getId());
            }
        }
        log.info("Cobro {} anulado por {}: {}", cobroId, usuario, motivoLimpio);
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

    /**
     * Una constancia de detracción o un comprobante de retención solo se registra en una venta que tiene
     * esa deducción, y por no más de lo que falta acreditar de ella.
     */
    private void validarDeduccion(Venta venta, Cobro.MetodoPago metodo, BigDecimal monto) {
        Venta.TipoDeduccion esperado = metodo == Cobro.MetodoPago.DETRACCION
                ? Venta.TipoDeduccion.DETRACCION : Venta.TipoDeduccion.RETENCION;
        if (venta.getTipoDeduccion() != esperado) {
            throw new IllegalArgumentException("La venta #" + venta.getId() + " no tiene "
                    + (esperado == Venta.TipoDeduccion.DETRACCION ? "detracción" : "retención")
                    + (venta.getTipoDeduccion() == Venta.TipoDeduccion.NINGUNA ? "." : " (tiene " + venta.getTipoDeduccion() + ")."));
        }
        BigDecimal registrada = deduccionesPorVenta(List.of(venta)).getOrDefault(venta.getId(), BigDecimal.ZERO);
        BigDecimal pendiente = deduccionPendiente(venta, registrada);
        if (monto.compareTo(pendiente) > 0) {
            throw new ReglaNegocioException("La " + (esperado == Venta.TipoDeduccion.DETRACCION ? "detracción" : "retención")
                    + " pendiente de la venta #" + venta.getId() + " es " + pendiente + " " + venta.getMoneda()
                    + " y se intenta registrar " + monto + ".");
        }
    }

    /** Parte de la detracción o retención que todavía no se acreditó (0 si la venta no tiene). */
    private static BigDecimal deduccionPendiente(Venta venta, BigDecimal registrada) {
        if (venta.getMontoDeduccion() == null || venta.getTipoDeduccion() == Venta.TipoDeduccion.NINGUNA) {
            return BigDecimal.ZERO;
        }
        return venta.getMontoDeduccion().subtract(registrada).max(BigDecimal.ZERO);
    }

    private Map<Long, BigDecimal> deduccionesPorVenta(List<Venta> ventas) {
        Map<Long, BigDecimal> mapa = new HashMap<>();
        if (ventas.isEmpty()) {
            return mapa;
        }
        for (Object[] fila : cobroRepository.deduccionesRegistradasPorVenta(ventas.stream().map(Venta::getId).toList())) {
            mapa.put((Long) fila[0], (BigDecimal) fila[1]);
        }
        return mapa;
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
