package com.bomerp.ventas.venta.event;

/**
 * Se publica al registrar una venta (directa o desde una proforma), dentro de la misma transacción.
 * Lo escuchan Facturación (emite el comprobante) y Postventa (programa los mantenimientos):
 * así Ventas no depende de esos módulos (ADR-002), y si un oyente falla la venta tampoco se guarda.
 *
 * @param rutina rutina de mantenimiento elegida al vender (ya validada); null = sin mantenimientos
 */
public record VentaRegistradaEvent(Long ventaId, RutinaMantenimiento rutina) {

    public VentaRegistradaEvent(Long ventaId) {
        this(ventaId, null);
    }
}
