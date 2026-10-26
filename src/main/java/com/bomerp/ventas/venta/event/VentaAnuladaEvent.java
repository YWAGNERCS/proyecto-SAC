package com.bomerp.ventas.venta.event;

/**
 * Se publica al anular una venta, dentro de la misma transacción.
 * Lo escucha Cobranza para dejar pendiente la devolución de lo que el cliente ya pagó:
 * así Ventas no depende de Cobranza (Cobranza ya depende de Ventas).
 */
public record VentaAnuladaEvent(Long ventaId) {
}
