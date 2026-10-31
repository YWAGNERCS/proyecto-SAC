package com.bomerp.ventas;

import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.ventas.detalleventa.entity.DetalleVenta;
import com.bomerp.ventas.venta.entity.Venta;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** RF-32: cada línea vendida guarda el costo del producto, en la moneda de la venta; totales con descuento e IGV. */
class VentaCostoTest {

    private static Producto producto(Producto.Moneda moneda, String costo) {
        Producto p = new Producto();
        p.setSector(Producto.Sector.MATERIAL_SERVICIO);
        p.setMoneda(moneda);
        p.setCostoBase(new BigDecimal(costo));
        p.setMargenPorcentaje(new BigDecimal("25"));
        return p;
    }

    private static Venta venta(Venta.Moneda moneda, String tipoCambio) {
        Venta v = new Venta();
        v.setMoneda(moneda);
        v.setTipoCambio(tipoCambio == null ? null : new BigDecimal(tipoCambio));
        return v;
    }

    @Test
    void mismaMonedaGuardaElCostoTalCual() {
        Venta v = venta(Venta.Moneda.SOLES, null);
        DetalleVenta d = new DetalleVenta(producto(Producto.Moneda.SOLES, "480"), 2, new BigDecimal("600"));
        v.agregarDetalle(d);

        assertThat(d.getCostoUnitario()).isEqualByComparingTo("480.00");
    }

    @Test
    void unProductoEnDolaresVendidoEnSolesSeConvierte() {
        Venta v = venta(Venta.Moneda.SOLES, "3.80");
        DetalleVenta d = new DetalleVenta(producto(Producto.Moneda.DOLARES, "100"), 1, new BigDecimal("475"));
        v.agregarDetalle(d);

        assertThat(d.getCostoUnitario()).isEqualByComparingTo("380.00");
    }

    @Test
    void sinTipoDeCambioNoSeInventaElCosto() {
        Venta v = venta(Venta.Moneda.DOLARES, null);
        DetalleVenta d = new DetalleVenta(producto(Producto.Moneda.SOLES, "380"), 1, new BigDecimal("125"));
        v.agregarDetalle(d);

        assertThat(d.getCostoUnitario()).isNull();
    }

    @Test
    void totalesConDescuentoEIgv() {
        Venta v = venta(Venta.Moneda.SOLES, null);
        // 2 × 100 con 10 % de descuento = 180; IGV 18 % = 32.40; total 212.40
        v.agregarDetalle(new DetalleVenta(producto(Producto.Moneda.SOLES, "80"), 2, new BigDecimal("100"), new BigDecimal("10")));

        assertThat(v.calcularSubtotal()).isEqualByComparingTo("180.00");
        assertThat(v.calcularIgv()).isEqualByComparingTo("32.40");
        assertThat(v.calcularTotal()).isEqualByComparingTo("212.40");
    }

    @Test
    void elPrecioDeVentaSaleDelCostoYElMargen() {
        assertThat(producto(Producto.Moneda.SOLES, "480").calcularPrecioVenta()).isEqualByComparingTo("600");
    }
}
