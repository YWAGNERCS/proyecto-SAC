package com.bomerp.cobranza.cobro.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Venta a crédito con saldo pendiente.
 * diasAtraso > 0 si ya pasó la fecha límite de pago (0 si aún no vence).
 * Si la venta tiene detracción o retención, deduccionPendiente es la parte que falta acreditar con la
 * constancia o el comprobante de retención, y netoPorCobrar lo que el cliente todavía debe pagarle a JD.
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
        BigDecimal saldo,
        String tipoDeduccion,
        BigDecimal montoDeduccion,
        BigDecimal deduccionPendiente,
        BigDecimal netoPorCobrar
) {}
