package com.bomerp.ventas.tributos;

import com.bomerp.clientes.general.integration.Cliente;
import com.bomerp.catalogo.producto.entity.ClasificacionDetraccion;
import com.bomerp.config.ReglaNegocioException;
import com.bomerp.ventas.venta.entity.Venta;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Nuevas ventas: solo detraccion. Las retenciones previas se conservan para su cobranza historica. */
@Service
public class DeduccionTributariaService {
    private final TributosProperties tributos;
    public DeduccionTributariaService(TributosProperties tributos) { this.tributos = tributos; }

    public void aplicar(Venta venta) {
        if (venta.getId() != null) throw new ReglaNegocioException("No se recalculan deducciones de ventas historicas.");
        venta.setTipoDeduccion(Venta.TipoDeduccion.NINGUNA);
        venta.setPorcentajeDeduccion(null);
        venta.setMontoDeduccion(null);
        venta.setCodigoDetraccion(null);
        venta.setMontoDetraccionSoles(null);
        // Facturación emite factura a EMPRESA y boleta a PERSONA_NATURAL.
        if (venta.getCliente().getTipo() != Cliente.TipoCliente.EMPRESA) return;
        BigDecimal total = venta.calcularTotal();
        if (enSoles(venta, total).compareTo(tributos.umbralSoles()) <= 0) return;
        var servicios = venta.getDetalles().stream().filter(d -> d.getProducto().esServicio()).toList();
        if (servicios.stream().anyMatch(d -> d.getProducto().getClasificacionDetraccion() == null)) {
            throw new ReglaNegocioException("Clasifique la detracción de los servicios en Catálogo antes de facturar: "
                    + servicios.stream().filter(d -> d.getProducto().getClasificacionDetraccion() == null)
                    .map(d -> String.valueOf(d.getProducto().getNombre())).toList());
        }
        var clases = servicios.stream().map(d -> d.getProducto().getClasificacionDetraccion())
                .distinct().toList();
        if (clases.size() > 1) {
            throw new ReglaNegocioException("La operación contiene servicios con distintos códigos de detracción. "
                    + "Separe las operaciones según su clasificación tributaria antes de emitir el comprobante.");
        }
        if (!clases.isEmpty() && clases.getFirst() != ClasificacionDetraccion.NO_SUJETO) {
            var clase = clases.getFirst();
            venta.setCodigoDetraccion(clase.codigo());
            // Politica de JD acreditada en su factura modelo E001-1901: deposito en soles enteros.
            BigDecimal depositoSoles = enSoles(venta, total).multiply(clase.porcentaje()).movePointLeft(2)
                    .setScale(0, RoundingMode.HALF_UP).setScale(2);
            venta.setTipoDeduccion(Venta.TipoDeduccion.DETRACCION);
            venta.setPorcentajeDeduccion(clase.porcentaje());
            venta.setMontoDetraccionSoles(depositoSoles);
            venta.setMontoDeduccion(venta.getMoneda() == Venta.Moneda.SOLES ? depositoSoles
                    : depositoSoles.divide(venta.getTipoCambio(), 2, RoundingMode.HALF_UP));
        }
    }

    public boolean admiteRetencion(Venta venta) {
        return venta.getTipoDeduccion() == Venta.TipoDeduccion.RETENCION; // Solo documentos historicos.
    }
    public BigDecimal porcentajeRetencion() { return tributos.retencionPorcentaje(); }
    public BigDecimal umbralSoles() { return tributos.umbralSoles(); }
    public static BigDecimal enSoles(Venta venta, BigDecimal total) {
        if (venta.getMoneda() == Venta.Moneda.SOLES) return total;
        if (venta.getTipoCambio() == null || venta.getTipoCambio().signum() <= 0) {
            throw new ReglaNegocioException("Se requiere un tipo de cambio válido para calcular las deducciones en dólares.");
        }
        return total.multiply(venta.getTipoCambio());
    }
}
