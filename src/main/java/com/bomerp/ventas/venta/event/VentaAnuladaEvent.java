package com.bomerp.ventas.venta.event;

import com.bomerp.ventas.venta.entity.Venta;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Se publica al anular una venta, dentro de la misma transacción. Lo escuchan Facturación (nota de crédito),
 * Postventa (cancela visitas) y Cobranza (devolución de lo pagado): así Ventas no depende de ninguno.
 *
 * ADR-008 (ADS S14): el evento lleva lo que sus oyentes necesitan, para que ninguno vuelva a preguntarle a Ventas
 * por la venta anulada. Antes era solo (ventaId) y Cobranza consultaba la venta de vuelta.
 * Al implementarlo apareció que los 6 campos del diseño (S11, S13) no alcanzaban: la devolución de una venta AL
 * CONTADO no tiene abonos en Cobranza, y lo pagado es el total de la venta si estaba pagada. Por eso van también
 * {@code total} y {@code pagada}. El stock NO viaja por aquí: se devuelve antes, por fachada, en la misma transacción.
 *
 * @param documento      número del comprobante ("F001-00000012"), o null si la venta no lo tuvo
 * @param motivo         motivo de la anulación (para el resumen semanal por motivo que previó S11)
 * @param total          total de la venta con IGV, en su moneda
 * @param pagada         si la venta estaba PAGADA al anularse (contado o crédito ya cancelado)
 */
public record VentaAnuladaEvent(Long ventaId, Long clienteId, Venta.Moneda moneda, LocalDate fechaAnulacion,
                                String documento, String motivo, BigDecimal total, boolean pagada) {
}
