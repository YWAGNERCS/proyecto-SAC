package com.bomerp.config.tributos;

import com.bomerp.ventas.venta.entity.Venta;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Decide si una venta tiene detracción (SPOT) o retención del IGV y calcula su monto.
 *
 * Regla (simplificada; confirmar con el contador de JD):
 *  - DETRACCIÓN: la venta incluye mano de obra (productos del sector SERVICIO) y su total supera
 *    el umbral (S/ 700). El cliente deposita el % en la cuenta de detracciones de JD en el Banco
 *    de la Nación. En soles el depósito se redondea a soles enteros, como pide SUNAT.
 *  - RETENCIÓN: si no hay detracción, el cliente es agente de retención y el total supera el umbral.
 *    El cliente se queda con el % y entrega a JD el comprobante de retención.
 *  - Detracción y retención no se aplican juntas a la misma operación.
 * Para ventas en dólares el umbral se compara en soles con el tipo de cambio de la venta.
 */
@Service
public class DeduccionTributariaService {

    /** Si una venta en dólares no tiene tipo de cambio, se estima el umbral con este valor (cota baja). */
    private static final BigDecimal TIPO_CAMBIO_MINIMO = new BigDecimal("3.00");

    private final TributosProperties tributos;

    public DeduccionTributariaService(TributosProperties tributos) {
        this.tributos = tributos;
    }

    /** Calcula y guarda en la venta su tipo de deducción, porcentaje y monto. Llamar con los detalles ya cargados. */
    public void aplicar(Venta venta) {
        BigDecimal total = venta.calcularTotal();
        boolean superaUmbral = totalEnSoles(venta, total).compareTo(tributos.umbralSoles()) > 0;
        boolean tieneServicios = venta.getDetalles().stream().anyMatch(d -> d.getProducto().esServicio());

        if (superaUmbral && tieneServicios) {
            fijar(venta, Venta.TipoDeduccion.DETRACCION, tributos.detraccionPorcentaje(), total,
                    venta.getMoneda() == Venta.Moneda.SOLES ? 0 : 2);
        } else if (superaUmbral && venta.getCliente().isAgenteRetencion()) {
            fijar(venta, Venta.TipoDeduccion.RETENCION, tributos.retencionPorcentaje(), total, 2);
        } else {
            venta.setTipoDeduccion(Venta.TipoDeduccion.NINGUNA);
            venta.setPorcentajeDeduccion(null);
            venta.setMontoDeduccion(null);
        }
    }

    private static void fijar(Venta venta, Venta.TipoDeduccion tipo, BigDecimal porcentaje, BigDecimal total, int decimales) {
        venta.setTipoDeduccion(tipo);
        venta.setPorcentajeDeduccion(porcentaje);
        venta.setMontoDeduccion(total.multiply(porcentaje).divide(BigDecimal.valueOf(100), decimales, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP));
    }

    private static BigDecimal totalEnSoles(Venta venta, BigDecimal total) {
        if (venta.getMoneda() == Venta.Moneda.SOLES) {
            return total;
        }
        BigDecimal tc = venta.getTipoCambio() != null ? venta.getTipoCambio() : TIPO_CAMBIO_MINIMO;
        return total.multiply(tc);
    }
}
