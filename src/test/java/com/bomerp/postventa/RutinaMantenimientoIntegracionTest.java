package com.bomerp.postventa;

import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.catalogo.producto.repository.ProductoRepository;
import com.bomerp.postventa.mantenimiento.entity.Mantenimiento;
import com.bomerp.postventa.mantenimiento.repository.MantenimientoRepository;
import com.bomerp.ventas.venta.dto.VentaResponseDTO;
import com.bomerp.ventas.venta.entity.Venta;
import com.bomerp.ventas.venta.event.RutinaMantenimiento;
import com.bomerp.ventas.venta.service.VentaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Venta real contra Oracle (se deshace al terminar: no deja ventas ni envía nada a SUNAT): con la rutina
 * del botón "Implementar rutinas de mantenimiento" se programan sus visitas; sin rutina, ninguna.
 */
// Necesita Oracle con los datos de desarrollo: la integración continua la salta (-DexcludedGroups=oracle)
@Tag("oracle")
@SpringBootTest
@Transactional
class RutinaMantenimientoIntegracionTest {

    @Autowired private VentaService ventaService;
    @Autowired private ProductoRepository productoRepository;
    @Autowired private MantenimientoRepository mantenimientoRepository;
    @Autowired private JdbcTemplate jdbc;

    private Long clienteId;
    private Long equipoId;

    @BeforeEach
    void preparar() {
        clienteId = jdbc.queryForObject("SELECT MIN(id) FROM clientes", Long.class);
        Producto equipo = new Producto();
        equipo.setNombre("Split de prueba (rutina)");
        equipo.setSector(Producto.Sector.CLIMATIZACION);
        equipo.setCostoBase(new BigDecimal("1000.00"));
        equipo.setMargenPorcentaje(new BigDecimal("20.00"));
        equipo.setMoneda(Producto.Moneda.SOLES);
        equipoId = productoRepository.save(equipo).getId();
    }

    private VentaResponseDTO vender(RutinaMantenimiento rutina) {
        return ventaService.registrarVentaDirecta(clienteId,
                List.of(new VentaService.ItemVentaDTO(equipoId, 1, BigDecimal.ZERO)),
                Venta.TipoPago.CONTADO, null, Venta.Moneda.SOLES, null, "prueba", "prueba", rutina);
    }

    private List<LocalDate> visitas(Long ventaId) {
        return mantenimientoRepository.findByVenta_IdAndEstado(ventaId, Mantenimiento.EstadoMantenimiento.PENDIENTE)
                .stream().map(Mantenimiento::getFechaProgramada).sorted(Comparator.naturalOrder()).toList();
    }

    @Test
    void conRutinaSeProgramanSusVisitas() {
        LocalDate hoy = LocalDate.now();
        VentaResponseDTO venta = vender(new RutinaMantenimiento(2, 6, null));
        assertThat(visitas(venta.id())).containsExactly(
                hoy.plusMonths(2), hoy.plusMonths(4), hoy.plusMonths(6),
                hoy.plusMonths(8), hoy.plusMonths(10), hoy.plusMonths(12));
    }

    @Test
    void sinRutinaNoSeProgramaNada() {
        VentaResponseDTO venta = vender(null);
        assertThat(visitas(venta.id())).isEmpty();
    }
}
