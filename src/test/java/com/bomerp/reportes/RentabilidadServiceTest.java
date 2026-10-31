package com.bomerp.reportes;

import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.clientes.general.integration.Cliente;
import com.bomerp.config.tipocambio.TipoCambioService;
import com.bomerp.reportes.rentabilidad.RentabilidadService;
import com.bomerp.servicios.orden.entity.MaterialOrden;
import com.bomerp.servicios.orden.entity.OrdenTrabajo;
import com.bomerp.servicios.orden.repository.OrdenTrabajoRepository;
import com.bomerp.ventas.detalleventa.entity.DetalleVenta;
import com.bomerp.ventas.venta.entity.Venta;
import com.bomerp.ventas.venta.repository.VentaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** RF-32: ganancia por venta, cliente, producto y técnico, todo en soles. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RentabilidadServiceTest {

    @Mock VentaRepository ventaRepository;
    @Mock OrdenTrabajoRepository ordenRepository;
    @Mock TipoCambioService tipoCambioService;
    @InjectMocks RentabilidadService service;

    private static Producto producto(long id, String nombre, Producto.Sector sector, Producto.Moneda moneda, String costo) {
        Producto p = new Producto();
        p.setId(id);
        p.setNombre(nombre);
        p.setSector(sector);
        p.setMoneda(moneda);
        p.setCostoBase(new BigDecimal(costo));
        return p;
    }

    private static Venta venta(long id, Venta.Moneda moneda, String tc, String clienteNombre) {
        Cliente c = new Cliente();
        c.setId(id * 10);
        c.setNombreORazonSocial(clienteNombre);
        Venta v = new Venta();
        v.setId(id);
        v.setCliente(c);
        v.setMoneda(moneda);
        v.setTipoCambio(tc == null ? null : new BigDecimal(tc));
        v.setFechaVenta(LocalDate.of(2026, 10, 10));
        return v;
    }

    @Test
    void sumaIngresosYCostosEnSoles() {
        Producto gas = producto(1, "Gas R410A", Producto.Sector.MATERIAL_SERVICIO, Producto.Moneda.SOLES, "80");
        Producto equipo = producto(2, "Split 12 000", Producto.Sector.CLIMATIZACION, Producto.Moneda.DOLARES, "300");

        Venta enSoles = venta(1, Venta.Moneda.SOLES, null, "Cliente A");
        enSoles.agregarDetalle(new DetalleVenta(gas, 2, new BigDecimal("100")));           // ingreso 200, costo 160
        Venta enDolares = venta(2, Venta.Moneda.DOLARES, "3.50", "Cliente B");
        enDolares.agregarDetalle(new DetalleVenta(equipo, 1, new BigDecimal("400")));      // ingreso 1 400, costo 1 050

        List<DetalleVenta> lineas = new ArrayList<>(enSoles.getDetalles());
        lineas.addAll(enDolares.getDetalles());
        when(ventaRepository.lineasVendidas(any(), any())).thenReturn(lineas);
        when(ordenRepository.realizadasEntre(any(), any())).thenReturn(List.of());

        RentabilidadService.Rentabilidad r = service.calcular(null, null);

        assertThat(r.resumen().ingresos()).isEqualByComparingTo("1600.00");
        assertThat(r.resumen().costos()).isEqualByComparingTo("1210.00");
        assertThat(r.resumen().margen()).isEqualByComparingTo("390.00");
        assertThat(r.resumen().margenPorcentaje()).isEqualByComparingTo("24.4");
        // Mayor ganancia primero: el split (350) antes que el gas (40)
        assertThat(r.porProducto()).extracting(RentabilidadService.Fila::nombre).containsExactly("Split 12 000", "Gas R410A");
        assertThat(r.porCliente()).hasSize(2);
    }

    @Test
    void unaLineaSinCostoNoSeSumaYSeCuenta() {
        Producto gas = producto(1, "Gas", Producto.Sector.MATERIAL_SERVICIO, Producto.Moneda.SOLES, "80");
        Venta v = venta(3, Venta.Moneda.DOLARES, null, "Cliente C");   // en dólares y sin tipo de cambio
        v.agregarDetalle(new DetalleVenta(gas, 1, new BigDecimal("30")));
        when(ventaRepository.lineasVendidas(any(), any())).thenReturn(new ArrayList<>(v.getDetalles()));
        when(ordenRepository.realizadasEntre(any(), any())).thenReturn(List.of());

        RentabilidadService.Rentabilidad r = service.calcular(null, null);

        assertThat(r.resumen().lineasSinCosto()).isEqualTo(1);
        assertThat(r.resumen().ingresos()).isEqualByComparingTo("0");
    }

    @Test
    void elTecnicoGanaLaManoDeObraMenosSusMateriales() {
        Producto manoObra = producto(5, "Mantenimiento", Producto.Sector.SERVICIO, Producto.Moneda.SOLES, "60");
        Venta factura = venta(7, Venta.Moneda.SOLES, null, "Cliente D");
        factura.agregarDetalle(new DetalleVenta(manoObra, 1, new BigDecimal("150")));
        when(ventaRepository.lineasVendidas(any(), any())).thenReturn(List.of());
        when(ventaRepository.lineasDeVentas(any())).thenReturn(new ArrayList<>(factura.getDetalles()));

        OrdenTrabajo o = new OrdenTrabajo();
        o.setId(1L);
        o.setTecnico("Luis Pérez ");
        o.setVentaFacturadaId(7L);
        MaterialOrden m = new MaterialOrden();
        m.setCantidad(2);
        m.setCostoUnitario(new BigDecimal("15"));
        m.setMoneda("SOLES");
        o.getMateriales().add(m);
        when(ordenRepository.realizadasEntre(any(), any())).thenReturn(List.of(o));

        RentabilidadService.TecnicoFila t = service.calcular(null, null).porTecnico().get(0);

        assertThat(t.tecnico()).isEqualTo("Luis Pérez");
        assertThat(t.ingresos()).isEqualByComparingTo("150");
        assertThat(t.costoManoObra()).isEqualByComparingTo("60");
        assertThat(t.costoMateriales()).isEqualByComparingTo("30");
        assertThat(t.margen()).isEqualByComparingTo("60");
    }

    @Test
    void lasFechasAlRevesSeRechazan() {
        assertThatThrownBy(() -> service.calcular(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 1, 1)))
                .hasMessageContaining("posterior");
    }
}
