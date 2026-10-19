package com.bomerp.cobranza.cobro.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Venta a crédito con saldo pendiente.
 * diasAtraso > 0 si ya pasó la fecha límite de pago (0 si aún no vence).
 */
public record CuentaPorCobrarDTO(
        Long ventaId,
        String comprobante,
        Long clienteId,
        String clienteNombre,
        LocalDate fechaVenta,
        LocalDate fechaLimitePago,
        long diasAtraso,
        String moneda,
        BigDecimal total,
        BigDecimal cobrado,
        BigDecimal saldo
) {}
