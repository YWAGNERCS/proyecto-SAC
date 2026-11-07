package com.bomerp.ventas.venta.service;

import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.config.ReglaNegocioException;
import com.bomerp.ventas.detalleventa.entity.DetalleVenta;
import com.bomerp.ventas.venta.entity.Venta;
import com.bomerp.ventas.venta.event.RutinaMantenimiento;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Botón "Implementar rutinas de mantenimiento": qué rutina se acepta al vender. */
class RutinaMantenimientoTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 7);

    private static Venta venta(Producto.Sector... sectores) {
        Venta v = new Venta();
        v.setFechaVenta(HOY);
        for (Producto.Sector s : sectores) {
            Producto p = new Producto();
            p.setSector(s);
            v.agregarDetalle(new DetalleVenta(p, 1, BigDecimal.TEN, BigDecimal.ZERO));
        }
        return v;
    }

    @Test
    void sinRutinaNoSeProgramaNada() {
        assertThat(VentaService.validarRutina(venta(Producto.Sector.CLIMATIZACION), null)).isNull();
    }

    @Test
    void laPrimeraVisitaPorDefectoEsLaVentaMasLaFrecuencia() {
        RutinaMantenimiento r = VentaService.validarRutina(venta(Producto.Sector.REFRIGERACION, Producto.Sector.MATERIAL_SERVICIO),
                new RutinaMantenimiento(2, 6, null));
        assertThat(r.primeraVisita()).isEqualTo(HOY.plusMonths(2));
        assertThat(r.fechaVisita(5)).isEqualTo(HOY.plusMonths(12));
    }

    @Test
    void respetaLaPrimeraFechaElegida() {
        LocalDate primera = HOY.plusDays(15);
        RutinaMantenimiento r = VentaService.validarRutina(venta(Producto.Sector.CLIMATIZACION),
                new RutinaMantenimiento(3, 4, primera));
        assertThat(r.fechaVisita(0)).isEqualTo(primera);
        assertThat(r.fechaVisita(3)).isEqualTo(primera.plusMonths(9));
    }

    @Test
    void soloParaVentasConEquiposDeClimatizacionORefrigeracion() {
        assertThatThrownBy(() -> VentaService.validarRutina(
                venta(Producto.Sector.VENTILACION_FORZADA, Producto.Sector.SERVICIO), new RutinaMantenimiento(3, 4, null)))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void rechazaFrecuenciaVisitasOFechaFueraDeRango() {
        Venta v = venta(Producto.Sector.CLIMATIZACION);
        assertThatThrownBy(() -> VentaService.validarRutina(v, new RutinaMantenimiento(0, 4, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VentaService.validarRutina(v, new RutinaMantenimiento(13, 4, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VentaService.validarRutina(v, new RutinaMantenimiento(3, 25, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VentaService.validarRutina(v, new RutinaMantenimiento(3, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VentaService.validarRutina(v, new RutinaMantenimiento(3, 4, HOY.minusDays(1))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
