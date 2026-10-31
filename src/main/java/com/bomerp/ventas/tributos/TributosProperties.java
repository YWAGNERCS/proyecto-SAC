package com.bomerp.ventas.tributos;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Parámetros de la detracción (SPOT) y de la retención del IGV, en application.properties
 * (prefijo {@code app.tributos}). Si SUNAT los cambia, se actualizan aquí sin tocar el código.
 *
 * @param detraccionPorcentaje % de detracción de los servicios (mantenimiento y reparación: 12)
 * @param retencionPorcentaje  % que retienen los agentes de retención (3)
 * @param umbralSoles          la operación debe superar este importe, en soles e IGV incluido (700)
 */
@ConfigurationProperties(prefix = "app.tributos")
public record TributosProperties(
        BigDecimal detraccionPorcentaje,
        BigDecimal retencionPorcentaje,
        BigDecimal umbralSoles
) {
}
