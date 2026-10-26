package com.bomerp.cobranza.cobro.dto;

import java.math.BigDecimal;

/** Cuánto pagó el cliente de una venta (al contado o con abonos): se le devuelve si se anula. */
public record PagadoDeVentaDTO(Long ventaId, String moneda, BigDecimal pagado) {
}
