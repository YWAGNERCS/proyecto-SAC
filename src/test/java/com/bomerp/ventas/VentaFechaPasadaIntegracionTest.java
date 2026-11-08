package com.bomerp.ventas;

import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.catalogo.producto.repository.ProductoRepository;
import com.bomerp.ventas.venta.entity.Venta;
import com.bomerp.ventas.venta.service.VentaService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Auditoría M3 y M4 contra Oracle: una venta registrada con su fecha real (10 días atrás) cuenta el crédito desde
 * ese día, pero su comprobante sale con fecha de hoy (plazo de SUNAT); el asistente necesita el permiso
 * VENTA_FECHA_PASADA; y al anular, la venta guarda quién y por qué. Todo se deshace al terminar.
 */
// Necesita Oracle con los datos de desarrollo: la integración continua la salta (-DexcludedGroups=oracle)
@Tag("oracle")
@SpringBootTest(properties = "app.facturacion.reintento-inicial-ms=86400000")
@Transactional
class VentaFechaPasadaIntegracionTest {

    @Autowired private VentaService ventaService;
    @Autowired private ProductoRepository productoRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;

    private Long clienteId;
    private Long servicioId;
    private final LocalDate hoy = LocalDate.now();

    @BeforeEach
    void preparar() {
        clienteId = jdbc.queryForObject("SELECT MIN(id) FROM clientes WHERE agente_retencion = 0", Long.class);
        Producto servicio = new Producto();
        servicio.setNombre("Visita de prueba (fecha pasada)");
        servicio.setSector(Producto.Sector.SERVICIO);
        servicio.setCostoBase(new BigDecimal("100.00"));
        servicio.setMargenPorcentaje(BigDecimal.ZERO);
        servicio.setMoneda(Producto.Moneda.SOLES);
        servicioId = productoRepository.save(servicio).getId();
    }

    @AfterEach
    void cerrarSesion() {
        SecurityContextHolder.clearContext();
    }

    private void comoUsuario(String... roles) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("prueba", null, roles));
    }

    private Long venderACredito(LocalDate fecha) {
        Long id = ventaService.registrarVentaDirecta(clienteId,
                List.of(new VentaService.ItemVentaDTO(servicioId, 1, BigDecimal.ZERO)),
                Venta.TipoPago.CREDITO, 30, Venta.Moneda.SOLES, null, "prueba", "prueba", null, fecha).id();
        em.flush();
        return id;
    }

    private LocalDate fecha(String sql, Long ventaId) {
        return jdbc.queryForObject(sql, java.sql.Date.class, ventaId).toLocalDate();
    }

    @Test
    void ventaConFechaPasadaCuentaElCreditoDesdeEseDiaPeroLaFacturaSaleHoy() {
        comoUsuario("ROLE_ADMIN");
        Long ventaId = venderACredito(hoy.minusDays(10));

        assertThat(fecha("SELECT fecha_venta FROM ventas WHERE id = ?", ventaId)).isEqualTo(hoy.minusDays(10));
        // 30 días de crédito desde la venta, no desde hoy
        assertThat(fecha("SELECT fecha_limite_pago FROM ventas WHERE id = ?", ventaId)).isEqualTo(hoy.plusDays(20));
        // El comprobante lleva la fecha de hoy: siempre dentro del plazo de 3 días de SUNAT
        assertThat(fecha("SELECT fecha_emision FROM comprobantes WHERE venta_id = ? AND reemplazado = FALSE", ventaId))
                .isEqualTo(hoy);
    }

    @Test
    void elAsistenteNecesitaElPermisoParaUnaFechaPasada() {
        int antes = jdbc.queryForObject("SELECT COUNT(*) FROM ventas", Integer.class);
        comoUsuario("ROLE_ASISTENTE");
        assertThatThrownBy(() -> venderACredito(hoy.minusDays(3)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("No tiene permiso");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ventas", Integer.class)).isEqualTo(antes);

        // Con la fecha de hoy no necesita nada
        assertThat(venderACredito(null)).isNotNull();

        // Con el permiso delegado por Lisandro (Configuración › Usuarios y permisos), sí
        comoUsuario("ROLE_ASISTENTE", "ROLE_PERM_VENTA_FECHA_PASADA");
        Long ventaId = venderACredito(hoy.minusDays(3));
        assertThat(fecha("SELECT fecha_venta FROM ventas WHERE id = ?", ventaId)).isEqualTo(hoy.minusDays(3));
    }

    @Test
    void alAnularLaVentaGuardaQuienYPorQue() {
        comoUsuario("ROLE_ADMIN");
        Long ventaId = venderACredito(null);
        ventaService.anular(ventaId, "  El cliente desistió del servicio  ", "lisandro");
        em.flush();

        var fila = jdbc.queryForMap("SELECT estado, anulado_por, motivo_anulacion FROM ventas WHERE id = ?", ventaId);
        assertThat(fila.get("ESTADO")).isEqualTo("ANULADA");
        assertThat(fila.get("ANULADO_POR")).isEqualTo("lisandro");
        assertThat(fila.get("MOTIVO_ANULACION")).isEqualTo("El cliente desistió del servicio");
    }
}
