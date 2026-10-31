package com.bomerp.ventas.venta.service;

import java.util.Optional;

/**
 * Lo que Ventas necesita saber de los comprobantes para sus reportes. Lo implementa Facturación
 * (inversión de dependencias, ADR-002): Ventas define qué necesita y no depende de Facturación.
 */
public interface ComprobantesDeVenta {

    /** "F001-00000012", o vacío si la venta no tiene comprobante. */
    Optional<String> numeroComprobanteDeVenta(Long ventaId);

    /** Número de la nota de crédito que anuló la venta, o vacío. */
    Optional<String> numeroNotaCreditoDeVenta(Long ventaId);
}
