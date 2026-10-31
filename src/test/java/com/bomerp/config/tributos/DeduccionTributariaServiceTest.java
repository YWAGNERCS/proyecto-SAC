package com.bomerp.config.tributos;

import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.clientes.general.integration.Cliente;
import com.bomerp.ventas.detalleventa.entity.DetalleVenta;
import com.bomerp.ventas.venta.entity.Venta;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** RF-24: detracción (12 % en servicios) y retención (3 % si el cliente es agente), sobre S/ 700. */
class DeduccionTributariaServiceTest {

    private final DeduccionTributariaService service = new DeduccionTributariaService(
            new TributosProperties(new BigDecimal("12"), new BigDecimal("3"), new BigDecimal("700")));

    private static Venta venta(Venta.Moneda moneda, boolean agenteRetencion, Producto.Sector sector, String precioSinIgv) {
        Cliente cliente = new Cliente();
        cliente.setAgenteRetencion(agenteRetencion);
        Venta v = new Venta();
        v.setCliente(cliente);
        v.setMoneda(moneda);
        Producto p = new Producto();
        p.setSector(sector);
        p.setMoneda(Producto.Moneda.SOLES);
        v.agregarDetalle(new DetalleVenta(p, 1, new BigDecimal(precioSinIgv)));
        return v;
    }

    @Test
    void unServicioSobreElUmbralLlevaDetraccionEnSolesEnteros() {
        // 1 000 + IGV = 1 180 → 12 % = 141.60 → se redondea a soles enteros: 142
        Venta v = venta(Venta.Moneda.SOLES, false, Producto.Sector.SERVICIO, "1000");
        service.aplicar(v);

        assertThat(v.getTipoDeduccion()).isEqualTo(Venta.TipoDeduccion.DETRACCION);
        assertThat(v.getMontoDeduccion()).isEqualByComparingTo("142.00");
    }

    @Test
    void unAgenteDeRetencionSinServiciosLlevaRetencion() {
        Venta v = venta(Venta.Moneda.SOLES, true, Producto.Sector.CLIMATIZACION, "1000");
        service.aplicar(v);

        assertThat(v.getTipoDeduccion()).isEqualTo(Venta.TipoDeduccion.RETENCION);
        assertThat(v.getMontoDeduccion()).isEqualByComparingTo("35.40");   // 3 % de 1 180
    }

    @Test
    void laDetraccionGanaALaRetencion() {
        Venta v = venta(Venta.Moneda.SOLES, true, Producto.Sector.SERVICIO, "1000");
        service.aplicar(v);

        assertThat(v.getTipoDeduccion()).isEqualTo(Venta.TipoDeduccion.DETRACCION);
    }

    @Test
    void bajoElUmbralNoHayDeduccion() {
        // 590 + IGV = 696.20: no supera S/ 700
        Venta v = venta(Venta.Moneda.SOLES, true, Producto.Sector.SERVICIO, "590");
        service.aplicar(v);

        assertThat(v.getTipoDeduccion()).isEqualTo(Venta.TipoDeduccion.NINGUNA);
        assertThat(v.getMontoDeduccion()).isNull();
    }

    @Test
    void enDolaresElUmbralSeMideEnSolesConElTipoDeCambio() {
        // US$ 200 + IGV = 236 → × 3.75 = S/ 885: supera el umbral; la detracción queda en dólares con centavos
        Venta v = venta(Venta.Moneda.DOLARES, false, Producto.Sector.SERVICIO, "200");
        v.setTipoCambio(new BigDecimal("3.75"));
        service.aplicar(v);

        assertThat(v.getTipoDeduccion()).isEqualTo(Venta.TipoDeduccion.DETRACCION);
        assertThat(v.getMontoDeduccion()).isEqualByComparingTo("28.32");
    }
}
