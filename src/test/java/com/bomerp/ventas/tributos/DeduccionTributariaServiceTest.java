package com.bomerp.ventas.tributos;

import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.catalogo.producto.entity.ClasificacionDetraccion;
import com.bomerp.clientes.general.integration.Cliente;
import com.bomerp.ventas.detalleventa.entity.DetalleVenta;
import com.bomerp.ventas.venta.entity.Venta;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RF-24: detracción (12 % en servicios) y retención (3 % si el cliente es agente), sobre S/ 700. */
class DeduccionTributariaServiceTest {

    private final DeduccionTributariaService service = new DeduccionTributariaService(
            new TributosProperties(new BigDecimal("12"), new BigDecimal("3"), new BigDecimal("700")));

    private static Venta venta(Venta.Moneda moneda, boolean agenteRetencion, Producto.Sector sector, String precioSinIgv) {
        Cliente cliente = new Cliente();
        cliente.setAgenteRetencion(agenteRetencion);
        cliente.setTipo(Cliente.TipoCliente.EMPRESA);
        Venta v = new Venta();
        v.setCliente(cliente);
        v.setMoneda(moneda);
        Producto p = new Producto();
        p.setSector(sector);
        p.setClasificacionDetraccion(ClasificacionDetraccion.MANTENIMIENTO_REPARACION);
        p.setMoneda(Producto.Moneda.SOLES);
        v.agregarDetalle(new DetalleVenta(p, 1, new BigDecimal(precioSinIgv)));
        return v;
    }

    @Test
    void unServicioSobreElUmbralRedondeaElDepositoSegunElModeloJD() {
        // Politica JD: 1 000 + IGV = 1 180; 12 % = 141.60; deposito redondeado = 142.00.
        Venta v = venta(Venta.Moneda.SOLES, false, Producto.Sector.SERVICIO, "1000");
        service.aplicar(v);

        assertThat(v.getTipoDeduccion()).isEqualTo(Venta.TipoDeduccion.DETRACCION);
        assertThat(v.getMontoDeduccion()).isEqualByComparingTo("142.00");
    }

    @Test
    void unClienteHistoricoAgenteNoActivaRetencionEnVentasNuevas() {
        Venta v = venta(Venta.Moneda.SOLES, true, Producto.Sector.CLIMATIZACION, "1000");
        service.aplicar(v);

        assertThat(v.getTipoDeduccion()).isEqualTo(Venta.TipoDeduccion.NINGUNA);
        assertThat(v.getMontoDeduccion()).isNull();
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
        assertThat(v.getMontoDeduccion()).isEqualByComparingTo("28.27");
    }

    @Test
    void unaBoletaNoLlevaDeducciones() {
        Venta v = venta(Venta.Moneda.SOLES, true, Producto.Sector.SERVICIO, "1000");
        v.getCliente().setTipo(Cliente.TipoCliente.PERSONA_NATURAL);
        service.aplicar(v);
        assertThat(v.getTipoDeduccion()).isEqualTo(Venta.TipoDeduccion.NINGUNA);
    }
    @Test
    void construccionTieneSuCodigoYPorcentaje() {
        Venta v = venta(Venta.Moneda.SOLES, false, Producto.Sector.SERVICIO, "1000");
        v.getDetalles().getFirst().getProducto().setClasificacionDetraccion(ClasificacionDetraccion.CONSTRUCCION);
        service.aplicar(v);
        assertThat(v.getCodigoDetraccion()).isEqualTo("030");
        assertThat(v.getPorcentajeDeduccion()).isEqualByComparingTo("4");
        assertThat(v.getMontoDeduccion()).isEqualByComparingTo("47.00");
    }
    @Test
    void noInfiereMantenimientoParaUnServicioSinClasificar() {
        Venta v = venta(Venta.Moneda.SOLES, false, Producto.Sector.SERVICIO, "1000");
        v.getDetalles().getFirst().getProducto().setClasificacionDetraccion(null);
        assertThatThrownBy(() -> service.aplicar(v)).hasMessageContaining("Cat");
    }
    @Test
    void noEstimaUnTipoDeCambioFicticio() {
        Venta v = venta(Venta.Moneda.DOLARES, true, Producto.Sector.CLIMATIZACION, "200");
        assertThatThrownBy(() -> service.aplicar(v)).hasMessageContaining("tipo de cambio");
    }
    @Test
    void unServicioNoSujetoNoGeneraRetencionNueva() {
        Venta v = venta(Venta.Moneda.SOLES, true, Producto.Sector.SERVICIO, "1000");
        v.getDetalles().getFirst().getProducto().setClasificacionDetraccion(ClasificacionDetraccion.NO_SUJETO);
        service.aplicar(v);
        assertThat(v.getTipoDeduccion()).isEqualTo(Venta.TipoDeduccion.NINGUNA);
        assertThat(v.getCodigoDetraccion()).isNull();
    }
    @Test
    void noAplicaDetraccionAUnServicioExcluidoPorMezclarOperaciones() {
        Venta v = venta(Venta.Moneda.SOLES, false, Producto.Sector.SERVICIO, "1000");
        Producto excluido = new Producto(); excluido.setSector(Producto.Sector.SERVICIO);
        excluido.setClasificacionDetraccion(ClasificacionDetraccion.NO_SUJETO);
        v.agregarDetalle(new DetalleVenta(excluido, 1, new BigDecimal("1000")));
        assertThatThrownBy(() -> service.aplicar(v)).hasMessageContaining("distintos");
    }

    @Test
    void rechazaRecalcularUnaVentaHistoricaSinModificarSusImportes() {
        Venta v = venta(Venta.Moneda.SOLES, true, Producto.Sector.CLIMATIZACION, "1000");
        v.setId(99L); v.setTipoDeduccion(Venta.TipoDeduccion.RETENCION);
        v.setPorcentajeDeduccion(new BigDecimal("3")); v.setMontoDeduccion(new BigDecimal("35.40"));
        assertThatThrownBy(() -> service.aplicar(v)).hasMessageContaining("historicas");
        assertThat(v.getTipoDeduccion()).isEqualTo(Venta.TipoDeduccion.RETENCION);
        assertThat(v.getMontoDeduccion()).isEqualByComparingTo("35.40");
    }
}
