package com.bomerp.compras.pago.service;

import com.bomerp.compras.compra.entity.Compra;
import com.bomerp.compras.compra.repository.CompraRepository;
import com.bomerp.compras.pago.entity.PagoProveedor;
import com.bomerp.compras.pago.repository.PagoProveedorRepository;
import com.bomerp.config.RecursoNoEncontradoException;
import com.bomerp.config.ReglaNegocioException;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cuentas por pagar: lo que JD le debe a sus proveedores por compras a crédito, y los pagos.
 * Espejo de Cobranza, pero hacia afuera.
 *
 * Reglas: solo se paga una compra a crédito PENDIENTE; el pago no supera el saldo, no es anterior
 * a la compra ni futuro; el mismo N° de operación con el mismo método a un proveedor no se registra
 * dos veces (409). Cuando lo pagado cubre el total la compra pasa a PAGADA; anular un pago la
 * devuelve a PENDIENTE.
 */
@Service
@Transactional
@Slf4j
public class CuentasPorPagarService {

    private final PagoProveedorRepository pagoRepository;
    private final CompraRepository compraRepository;

    public CuentasPorPagarService(PagoProveedorRepository pagoRepository, CompraRepository compraRepository) {
        this.pagoRepository = pagoRepository;
        this.compraRepository = compraRepository;
    }

    // ── DTO ───────────────────────────────────────────────────

    public record PagoRequest(
            @NotNull(message = "Indique la compra") Long compraId,
            LocalDate fechaPago,                                   // opcional: hoy
            @NotNull(message = "Indique el monto")
            @DecimalMin(value = "0.01", message = "El monto debe ser mayor que 0") BigDecimal monto,
            @NotNull(message = "Indique el método de pago") PagoProveedor.Metodo metodo,
            @Size(max = 50) String numeroOperacion,
            @Size(max = 300) String observacion
    ) {}

    public record Anulacion(@NotNull @Size(min = 5, max = 300, message = "Indique el motivo (5 a 300 caracteres)") String motivo) {}

    public record PagoDTO(Long id, Long compraId, String numeroFactura, Long proveedorId, String proveedor, LocalDate fechaPago,
                          BigDecimal monto, String moneda, String metodo, String numeroOperacion, String observacion,
                          String estado, LocalDate fechaAnulacion, String motivoAnulacion, String anuladoPor,
                          String registradoPor) {
        static PagoDTO de(PagoProveedor p) {
            Compra c = p.getCompra();
            return new PagoDTO(p.getId(), c.getId(), c.getNumeroFactura(), c.getProveedor().getId(),
                    c.getProveedor().getNombreORazonSocial(), p.getFechaPago(), p.getMonto(), c.getMoneda().name(),
                    p.getMetodo().name(), p.getNumeroOperacion(), p.getObservacion(), p.getEstado().name(),
                    p.getFechaAnulacion(), p.getMotivoAnulacion(), p.getAnuladoPor(), p.getRegistradoPor());
        }
    }

    /** Una compra a crédito con saldo. diasAtraso &gt; 0 si ya venció. */
    public record CuentaPorPagarDTO(Long compraId, String numeroFactura, Long proveedorId, String proveedor,
                                    String proveedorTelefono, LocalDate fechaCompra, LocalDate fechaVencimiento,
                                    String moneda, BigDecimal total, BigDecimal pagado, BigDecimal saldo,
                                    long diasAtraso) {}

    /** Totales por moneda: lo que se debe, lo ya vencido y lo que vence en los próximos 7 días. */
    public record ResumenMoneda(String moneda, BigDecimal porPagar, BigDecimal vencido, BigDecimal venceEnSieteDias,
                                int compras) {}

    public record CuentasDTO(List<CuentaPorPagarDTO> cuentas, List<ResumenMoneda> resumen) {}

    // ── Consultas ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CuentasDTO pendientes(Long proveedorId, boolean soloVencidas) {
        List<Compra> compras = pagoRepository.comprasPendientes(proveedorId);
        Map<Long, BigDecimal> pagado = pagadoPorCompra(compras);
        LocalDate hoy = LocalDate.now();

        List<CuentaPorPagarDTO> cuentas = compras.stream().map(c -> {
                    BigDecimal p = pagado.getOrDefault(c.getId(), BigDecimal.ZERO);
                    long atraso = c.getFechaVencimiento() != null && c.getFechaVencimiento().isBefore(hoy)
                            ? ChronoUnit.DAYS.between(c.getFechaVencimiento(), hoy) : 0;
                    return new CuentaPorPagarDTO(c.getId(), c.getNumeroFactura(), c.getProveedor().getId(),
                            c.getProveedor().getNombreORazonSocial(), c.getProveedor().getTelefono(), c.getFechaCompra(),
                            c.getFechaVencimiento(), c.getMoneda().name(), c.getTotal(), p,
                            c.getTotal().subtract(p).max(BigDecimal.ZERO), atraso);
                })
                .filter(c -> !soloVencidas || c.diasAtraso() > 0)
                .toList();

        Map<String, BigDecimal[]> porMoneda = new HashMap<>();
        Map<String, Integer> cantidad = new HashMap<>();
        for (CuentaPorPagarDTO c : cuentas) {
            BigDecimal[] t = porMoneda.computeIfAbsent(c.moneda(), m -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            t[0] = t[0].add(c.saldo());
            if (c.diasAtraso() > 0) {
                t[1] = t[1].add(c.saldo());
            } else if (c.fechaVencimiento() != null && !c.fechaVencimiento().isAfter(hoy.plusDays(7))) {
                t[2] = t[2].add(c.saldo());
            }
            cantidad.merge(c.moneda(), 1, Integer::sum);
        }
        List<ResumenMoneda> resumen = porMoneda.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(java.util.Comparator.reverseOrder()))   // SOLES antes que DOLARES
                .map(e -> new ResumenMoneda(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2],
                        cantidad.get(e.getKey())))
                .toList();
        return new CuentasDTO(cuentas, resumen);
    }

    @Transactional(readOnly = true)
    public List<PagoDTO> pagos(Long proveedorId, LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new IllegalArgumentException("La fecha 'desde' no puede ser posterior a 'hasta'.");
        }
        return pagoRepository.buscar(proveedorId, desde, hasta).stream().map(PagoDTO::de).toList();
    }

    @Transactional(readOnly = true)
    public List<PagoDTO> pagosDeCompra(Long compraId) {
        compra(compraId);
        return pagoRepository.deCompra(compraId).stream().map(PagoDTO::de).toList();
    }

    /** Lo pagado de cada compra (para el listado de Compras). */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> pagadoPorCompra(List<Compra> compras) {
        Map<Long, BigDecimal> pagado = new HashMap<>();
        List<Long> ids = compras.stream().map(Compra::getId).toList();
        if (!ids.isEmpty()) {
            for (Object[] fila : pagoRepository.pagadoPorCompra(ids)) {
                pagado.put((Long) fila[0], (BigDecimal) fila[1]);
            }
        }
        return pagado;
    }

    // ── Operaciones ───────────────────────────────────────────

    /** @param usuario preferred_username de quien registra el pago */
    public PagoDTO registrar(PagoRequest dto, String usuario) {
        Compra c = compra(dto.compraId());
        if (c.getTipoPago() != Compra.TipoPago.CREDITO) {
            throw new ReglaNegocioException("La compra #" + c.getId() + " fue al contado: no tiene saldo por pagar.");
        }
        if (c.getEstadoPago() == Compra.EstadoPago.PAGADA) {
            throw new ReglaNegocioException("La compra #" + c.getId() + " ya está pagada.");
        }
        LocalDate fecha = dto.fechaPago() != null ? dto.fechaPago() : LocalDate.now();
        if (fecha.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La fecha del pago no puede ser futura.");
        }
        if (fecha.isBefore(c.getFechaCompra())) {
            throw new IllegalArgumentException("La fecha del pago no puede ser anterior a la compra (" + c.getFechaCompra() + ").");
        }
        BigDecimal monto = dto.monto().setScale(2, RoundingMode.HALF_UP);
        BigDecimal saldo = c.getTotal().subtract(pagoRepository.pagadoDeCompra(c.getId()));
        if (monto.compareTo(saldo) > 0) {
            throw new IllegalArgumentException("El pago (" + monto + ") supera el saldo de la compra (" + saldo + ").");
        }
        String operacion = dto.numeroOperacion() == null || dto.numeroOperacion().isBlank() ? null : dto.numeroOperacion().trim();
        if (operacion == null && dto.metodo() != PagoProveedor.Metodo.EFECTIVO) {
            throw new IllegalArgumentException("Indique el N° de operación (o de cheque) del pago.");
        }
        if (operacion != null) {
            String normal = operacion.toUpperCase().replace(" ", "");
            pagoRepository.mismaOperacion(c.getProveedor().getId(), dto.metodo(), normal).stream().findFirst()
                    .ifPresent(p -> {
                        throw new ReglaNegocioException("Ese N° de operación ya se registró en el pago #" + p.getId()
                                + " (compra #" + p.getCompra().getId() + "). Revise el historial antes de volver a pagar.");
                    });
        }

        PagoProveedor p = new PagoProveedor();
        p.setCompra(c);
        p.setFechaPago(fecha);
        p.setMonto(monto);
        p.setMetodo(dto.metodo());
        p.setNumeroOperacion(operacion);
        p.setObservacion(dto.observacion() == null || dto.observacion().isBlank() ? null : dto.observacion().trim());
        p.setRegistradoPor(usuario);
        p.setFechaRegistro(LocalDateTime.now());
        PagoProveedor guardado = pagoRepository.save(p);

        if (monto.compareTo(saldo) == 0) {
            c.setEstadoPago(Compra.EstadoPago.PAGADA);
            compraRepository.save(c);
        }
        log.info("Pago {} a {} por la compra {}: {} {} ({})", guardado.getId(), c.getProveedor().getNombreORazonSocial(),
                c.getId(), c.getMoneda(), monto, usuario);
        return PagoDTO.de(guardado);
    }

    /** Anula un pago mal registrado: deja de contar y la compra vuelve a PENDIENTE. */
    public PagoDTO anular(Long id, String motivo, String usuario) {
        PagoProveedor p = pagoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pago a proveedor no encontrado: " + id));
        if (p.getEstado() == PagoProveedor.Estado.ANULADO) {
            throw new ReglaNegocioException("El pago #" + id + " ya está anulado.");
        }
        p.setEstado(PagoProveedor.Estado.ANULADO);
        p.setFechaAnulacion(LocalDate.now());
        p.setMotivoAnulacion(motivo.trim());
        p.setAnuladoPor(usuario);
        Compra c = p.getCompra();
        c.setEstadoPago(Compra.EstadoPago.PENDIENTE);
        compraRepository.save(c);
        log.info("Pago {} a proveedor anulado por {}: {}", id, usuario, motivo);
        return PagoDTO.de(pagoRepository.save(p));
    }

    private Compra compra(Long id) {
        return compraRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Compra no encontrada: " + id));
    }
}
