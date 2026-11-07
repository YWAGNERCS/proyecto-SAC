package com.bomerp.facturacion;

import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.catalogo.producto.repository.ProductoRepository;
import com.bomerp.facturacion.comprobante.entity.Comprobante.EstadoSunat;
import com.bomerp.facturacion.facturacion.service.EnvioSunatService;
import com.bomerp.facturacion.ose.integration.OseClient;
import com.bomerp.facturacion.ose.integration.OseClient.RespuestaOse;
import com.bomerp.facturacion.ose.integration.OseClient.Resultado;
import com.bomerp.ventas.venta.entity.Venta;
import com.bomerp.ventas.venta.service.VentaService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Auditoría M10, contra Oracle: el comprobante de una venta anulada no sale a SUNAT sin su nota de crédito.
 *
 * Reproduce el caso de la venta #285: la venta quedó ANULADA con su factura PENDIENTE y sin nota. El envío
 * a SUNAT está reemplazado por un mock (no sale nada, ni a la beta) y todo se deshace al terminar.
 */
// Reintento automático apagado: con el mock, tomaría los comprobantes PENDIENTES reales de la base
@SpringBootTest(properties = "app.facturacion.reintento-inicial-ms=86400000")
@Transactional
class VentaAnuladaSinNotaIntegracionTest {

    @MockitoBean private OseClient ose;
    @Autowired private VentaService ventaService;
    @Autowired private EnvioSunatService envioSunat;
    @Autowired private ProductoRepository productoRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;

    private Long ventaId;
    private Long comprobanteId;

    @BeforeEach
    void venderUnEquipo() {
        Long clienteId = jdbc.queryForObject("SELECT MIN(id) FROM clientes WHERE agente_retencion = 0", Long.class);
        Producto equipo = new Producto();
        equipo.setNombre("Split de prueba (nota por anulación)");
        equipo.setSector(Producto.Sector.CLIMATIZACION);
        equipo.setCostoBase(new BigDecimal("500.00"));
        equipo.setMargenPorcentaje(new BigDecimal("20.00"));
        equipo.setMoneda(Producto.Moneda.SOLES);
        Long equipoId = productoRepository.save(equipo).getId();

        ventaId = ventaService.registrarVentaDirecta(clienteId,
                List.of(new VentaService.ItemVentaDTO(equipoId, 1, BigDecimal.ZERO)),
                Venta.TipoPago.CONTADO, null, Venta.Moneda.SOLES, null, "prueba", "prueba", null).id();
        em.flush();
        comprobanteId = jdbc.queryForObject("SELECT id FROM comprobantes WHERE venta_id = ?", Long.class, ventaId);
        // SUNAT "no responde": el comprobante sigue PENDIENTE y no se dispara el envío por correo
        when(ose.emitir(any())).thenReturn(new RespuestaOse(Resultado.REINTENTAR, null, "sin respuesta", null, null, null, null));
    }

    private int notasDelComprobante() {
        em.flush();
        return jdbc.queryForObject("SELECT COUNT(*) FROM notas_credito WHERE comprobante_id = ?", Integer.class, comprobanteId);
    }

    @Test
    void ventaAnuladaSinNotaRecibeSuNotaAntesDeEnviarElComprobante() {
        // Como la #285: ANULADA directo en la base, sin pasar por la anulación (que es la que crea la nota)
        jdbc.update("UPDATE ventas SET estado = 'ANULADA', fecha_anulacion = SYSDATE WHERE id = ?", ventaId);
        em.clear();
        assertThat(notasDelComprobante()).isZero();

        assertThat(envioSunat.enviarComprobante(comprobanteId)).isEqualTo(EstadoSunat.PENDIENTE);

        assertThat(notasDelComprobante()).isEqualTo(1);
        verify(ose).emitir(any());
        String estadoNota = jdbc.queryForObject("SELECT estado_sunat FROM notas_credito WHERE comprobante_id = ?",
                String.class, comprobanteId);
        assertThat(estadoNota).isEqualTo("PENDIENTE");   // sale recién cuando SUNAT acepte la factura
    }

    @Test
    void ventaAnuladaNormalmenteNoDuplicaLaNota() {
        ventaService.anular(ventaId);
        assertThat(notasDelComprobante()).isEqualTo(1);

        envioSunat.enviarComprobante(comprobanteId);

        assertThat(notasDelComprobante()).isEqualTo(1);
        verify(ose).emitir(any());
    }
}
