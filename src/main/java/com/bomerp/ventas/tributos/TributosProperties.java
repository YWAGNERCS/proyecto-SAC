package com.bomerp.ventas.tributos;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.math.BigDecimal;

/** Compatibilidad de configuracion: tasas SPOT por clasificacion del catalogo;
 * retencionPorcentaje solo para acreditar documentos historicos. */
@ConfigurationProperties(prefix = "app.tributos")
public record TributosProperties(BigDecimal detraccionPorcentaje, BigDecimal retencionPorcentaje, BigDecimal umbralSoles) {}
