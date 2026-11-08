package com.bomerp.facturacion;

import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.catalogo.producto.repository.ProductoRepository;
import com.bomerp.facturacion.comprobante.dto.EstadoSunatVentaDTO;
import com.bomerp.facturacion.comprobante.entity.Comprobante;
import com.bomerp.facturacion.comprobante.repository.ComprobanteRepository;
import com.bomerp.facturacion.facturacion.service.FacturacionService;
import com.bomerp.facturacion.ose.integration.OseClient;
import com.bomerp.ventas.venta.entity.Venta;
import com.bomerp.ventas.venta.service.VentaService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Factura con detracción sin la cuenta de detracciones de JD, contra Oracle (informe de rechazos SUNAT, D-6).
 *
 * Antes la factura tomaba número y esperaba a que se configurara la cuenta, mientras corría su plazo de 3 días
 * para llegar a SUNAT. Ahora la venta se guarda "por emitir", sin comprobante: no consume número ni corre plazo,
 * y el comprobante se emite solo cuando la cuenta está. El envío a SUNAT es un mock y todo se deshace al terminar.
 */
// Reintento y emisión automáticos apagados: se llaman a mano en la prueba
// Necesita Oracle con los datos de desarrollo: la integración continua la salta (-DexcludedGroups=oracle)
@Tag("oracle")
@SpringBootTest(properties = "app.facturacion.reintento-inicial-ms=86400000")
@Transactional
class VentaPorEmitirIntegracionTest {

    private static final String FALTA_CUENTA = "Falta configurar la cuenta de detracciones de JD (prueba)";

    @MockitoBean private OseClient ose;
    @Autowired private VentaService ventaService;
    @Autowired private FacturacionService facturacion;
    @Autowired private ComprobanteRepository comprobantes;
    @Autowired private ProductoRepository productoRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;

    private boolean cuentaConfigurada;
    private Long servicioId;
    private Long clienteId;

    @BeforeEach
    void preparar() {
        // Como SunatDirectoClient: sin cuenta, una venta con detracción no puede emitirse todavía
        cuentaConfigurada = false;
        when(ose.motivoParaNoEmitir(any())).thenAnswer(inv -> {
            Venta v = inv.getArgument(0);
            return !cuentaConfigurada && v.getTipoDeduccion() == Venta.TipoDeduccion.DETRACCION ? FALTA_CUENTA : null;
        });
        clienteId = jdbc.queryForObject(
                "SELECT MIN(id) FROM clientes WHERE agente_retencion = 0 AND ruc IS NOT NULL", Long.class);
        Producto servicio = new Producto();
        servicio.setNombre("Instalación de prueba (detracción)");
        servicio.setSector(Producto.Sector.SERVICIO);
        servicio.setCostoBase(new BigDecimal("1000.00"));
        servicio.setMargenPorcentaje(BigDecimal.ZERO);
        servicio.setMoneda(Producto.Moneda.SOLES);
        servicioId = productoRepository.save(servicio).getId();
    }

    private Long venderServicio() {
        Long id = ventaService.registrarVentaDirecta(clienteId,
                List.of(new VentaService.ItemVentaDTO(servicioId, 1, BigDecimal.ZERO)),
                Venta.TipoPago.CONTADO, null, Venta.Moneda.SOLES, null, "prueba", "prueba", null).id();
        em.flush();
        return id;
    }

    private int ultimoNumero(String serie) {
        return jdbc.queryForObject("SELECT ultimo_numero FROM series_comprobante WHERE serie = ?", Integer.class, serie);
    }

    @Test
    void sinCuentaLaVentaSeGuardaSinComprobanteNiNumeroYSeEmiteAlConfigurarla() {
        int f001Antes = ultimoNumero("F001");
        Long ventaId = venderServicio();

        assertThat(jdbc.queryForObject("SELECT tipo_deduccion FROM ventas WHERE id = ?", String.class, ventaId)).isEqualTo("DETRACCION");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comprobantes WHERE venta_id = ?", Integer.class, ventaId)).isZero();
        assertThat(ultimoNumero("F001")).isEqualTo(f001Antes);

        // Ventas la muestra "por emitir" con el motivo, y la campana la cuenta
        List<EstadoSunatVentaDTO> estados = facturacion.estadosDeVentas(List.of(ventaId));
        assertThat(estados).singleElement().satisfies(e -> {
            assertThat(e.comprobante()).isNull();
            assertThat(e.porEmitir()).isEqualTo(FALTA_CUENTA);
        });
        assertThat(facturacion.plazoDeEnvio().ventasPorEmitir()).contains(ventaId);

        // Emitirlo a mano tampoco gasta un número mientras falte la cuenta
        assertThatThrownBy(() -> facturacion.emitirComprobanteParaVenta(ventaId))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("por emitir");
        facturacion.emitirVentasPorEmitir();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comprobantes WHERE venta_id = ?", Integer.class, ventaId)).isZero();

        // Se configura la cuenta: la emisión automática le da su comprobante, con fecha de hoy
        cuentaConfigurada = true;
        facturacion.emitirVentasPorEmitir();
        em.flush();
        em.clear();
        Comprobante c = comprobantes.findByVentaIdAndReemplazadoFalse(ventaId).orElseThrow();
        assertThat(c.getSerie()).isEqualTo("F001");
        assertThat(Integer.parseInt(c.getNumero())).isEqualTo(f001Antes + 1);
        assertThat(c.getFechaEmision()).isEqualTo(java.time.LocalDate.now());
        assertThat(c.getEstadoSunat()).isEqualTo(Comprobante.EstadoSunat.PENDIENTE);
        assertThat(facturacion.plazoDeEnvio().ventasPorEmitir()).doesNotContain(ventaId);
        assertThat(facturacion.estadosDeVentas(List.of(ventaId))).singleElement()
                .satisfies(e -> assertThat(e.porEmitir()).isNull());
    }

    @Test
    void unaVentaPorEmitirSeAnulaSinNotaDeCredito() {
        Long ventaId = venderServicio();
        ventaService.anular(ventaId, "Prueba de anulación", "prueba");
        em.flush();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comprobantes WHERE venta_id = ?", Integer.class, ventaId)).isZero();
        // Anulada: ya no espera comprobante, ni al configurar la cuenta
        assertThat(facturacion.plazoDeEnvio().ventasPorEmitir()).doesNotContain(ventaId);
        cuentaConfigurada = true;
        facturacion.emitirVentasPorEmitir();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comprobantes WHERE venta_id = ?", Integer.class, ventaId)).isZero();
    }
}
