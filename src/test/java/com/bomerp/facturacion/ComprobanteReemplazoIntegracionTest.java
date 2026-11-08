package com.bomerp.facturacion;

import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.catalogo.producto.repository.ProductoRepository;
import com.bomerp.facturacion.comprobante.dto.EstadoSunatVentaDTO;
import com.bomerp.facturacion.comprobante.entity.Comprobante;
import com.bomerp.facturacion.comprobante.entity.Comprobante.EstadoSunat;
import com.bomerp.facturacion.comprobante.repository.ComprobanteRepository;
import com.bomerp.facturacion.facturacion.service.EnvioSunatService;
import com.bomerp.facturacion.facturacion.service.FacturacionService;
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
import org.springframework.dao.DataIntegrityViolationException;
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
 * Comprobante de reemplazo, contra Oracle (informe de rechazos SUNAT, D-2, D-3, D-4 y D-5).
 *
 * SUNAT rechaza con CDR el comprobante de una venta real: su número queda usado, así que se emite otro con
 * número nuevo para la MISMA venta, sin tocar stock, cobro ni mantenimientos. El envío a SUNAT es un mock
 * (no sale nada, ni a la beta) y todo se deshace al terminar.
 */
// Reintento automático apagado: con el mock, tomaría los comprobantes PENDIENTES reales de la base
@SpringBootTest(properties = "app.facturacion.reintento-inicial-ms=86400000")
@Transactional
class ComprobanteReemplazoIntegracionTest {

    @MockitoBean private OseClient ose;
    @Autowired private VentaService ventaService;
    @Autowired private FacturacionService facturacion;
    @Autowired private EnvioSunatService envioSunat;
    @Autowired private ComprobanteRepository comprobantes;
    @Autowired private ProductoRepository productoRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;

    private Long clienteId;
    private Long equipoId;
    private Long ventaId;
    private Long comprobanteId;

    @BeforeEach
    void venderUnEquipo() {
        clienteId = jdbc.queryForObject("SELECT MIN(id) FROM clientes WHERE agente_retencion = 0", Long.class);
        Producto equipo = new Producto();
        equipo.setNombre("Split de prueba (reemplazo)");
        equipo.setSector(Producto.Sector.CLIMATIZACION);
        equipo.setCostoBase(new BigDecimal("500.00"));
        equipo.setMargenPorcentaje(new BigDecimal("20.00"));
        equipo.setMoneda(Producto.Moneda.SOLES);
        equipoId = productoRepository.save(equipo).getId();

        ventaId = ventaService.registrarVentaDirecta(clienteId,
                List.of(new VentaService.ItemVentaDTO(equipoId, 1, BigDecimal.ZERO)),
                Venta.TipoPago.CONTADO, null, Venta.Moneda.SOLES, null, "prueba", "prueba", null).id();
        em.flush();
        comprobanteId = jdbc.queryForObject("SELECT id FROM comprobantes WHERE venta_id = ?", Long.class, ventaId);
        when(ose.emitir(any())).thenReturn(new RespuestaOse(Resultado.REINTENTAR, null, "sin respuesta", null, null, null, null));
    }

    /** Lo que SUNAT respondió al comprobante de la venta (directo en la base, como lo deja el envío). */
    private void respuestaDeSunat(String estado, int codigo) {
        jdbc.update("UPDATE comprobantes SET estado_sunat = ?, sunat_codigo = ?, sunat_intentos = 1 WHERE id = ?",
                estado, codigo, comprobanteId);
        em.clear();
    }

    private int contar(String sql) {
        return jdbc.queryForObject(sql, Integer.class, ventaId);
    }

    @Test
    void reemplazoEmiteOtroNumeroParaLaMismaVentaSinTocarStockCobroNiMantenimientos() {
        respuestaDeSunat("RECHAZADO", 2800);
        String numeroOriginal = jdbc.queryForObject("SELECT serie || '-' || numero FROM comprobantes WHERE id = ?", String.class, comprobanteId);
        BigDecimal stockAntes = jdbc.queryForObject("SELECT NVL(SUM(stock_actual), 0) FROM productos", BigDecimal.class);
        int lineas = contar("SELECT COUNT(*) FROM detalle_venta WHERE venta_id = ?");
        int cobros = contar("SELECT COUNT(*) FROM detalle_cobro WHERE venta_id = ?");
        int mantenimientos = contar("SELECT COUNT(*) FROM mantenimientos WHERE venta_id = ?");

        Comprobante nuevo = facturacion.emitirReemplazo(comprobanteId);
        em.flush();

        assertThat(nuevo.getId()).isNotEqualTo(comprobanteId);
        assertThat(nuevo.getVenta().getId()).isEqualTo(ventaId);
        assertThat(nuevo.getSerie() + "-" + nuevo.getNumero()).isNotEqualTo(numeroOriginal);
        assertThat(nuevo.getEstadoSunat()).isEqualTo(EstadoSunat.PENDIENTE);
        assertThat(nuevo.getReemplazaA().getId()).isEqualTo(comprobanteId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comprobantes WHERE id = ? AND reemplazado = TRUE", Integer.class, comprobanteId)).isOne();
        assertThat(jdbc.queryForObject("SELECT comprobante_id FROM ventas WHERE id = ?", Long.class, ventaId)).isEqualTo(nuevo.getId());
        assertThat(facturacion.numeroComprobanteDeVenta(ventaId)).contains(nuevo.getSerie() + "-" + nuevo.getNumero());

        // Solo cambió el documento tributario
        assertThat(jdbc.queryForObject("SELECT NVL(SUM(stock_actual), 0) FROM productos", BigDecimal.class)).isEqualByComparingTo(stockAntes);
        assertThat(contar("SELECT COUNT(*) FROM detalle_venta WHERE venta_id = ?")).isEqualTo(lineas);
        assertThat(contar("SELECT COUNT(*) FROM detalle_cobro WHERE venta_id = ?")).isEqualTo(cobros);
        assertThat(contar("SELECT COUNT(*) FROM mantenimientos WHERE venta_id = ?")).isEqualTo(mantenimientos);

        // Ventas ve el vigente y, en el historial de emisiones, el rechazado
        List<EstadoSunatVentaDTO> estados = facturacion.estadosDeVentas(List.of(ventaId));
        assertThat(estados).hasSize(1);
        assertThat(estados.get(0).comprobante().id()).isEqualTo(nuevo.getId());
        assertThat(estados.get(0).reemplazados()).singleElement().satisfies(d -> {
            assertThat(d.id()).isEqualTo(comprobanteId);
            assertThat(d.rechazoDefinitivo()).isTrue();
            assertThat(d.puedeReemplazar()).isFalse();
        });
    }

    @Test
    void soloSeReemplazaUnRechazoDefinitivo() {
        respuestaDeSunat("PENDIENTE", 0);
        assertThatThrownBy(() -> facturacion.emitirReemplazo(comprobanteId)).isInstanceOf(ResponseStatusException.class);

        respuestaDeSunat("ACEPTADO", 0);
        assertThatThrownBy(() -> facturacion.emitirReemplazo(comprobanteId))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("tiene validez");

        // Excepción: SUNAT no lo registró; se corrige y se reenvía con el mismo número
        respuestaDeSunat("RECHAZADO", 305);
        assertThatThrownBy(() -> facturacion.emitirReemplazo(comprobanteId))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("mismo número");

        assertThat(contar("SELECT COUNT(*) FROM comprobantes WHERE venta_id = ?")).isOne();
    }

    @Test
    void unRechazadoNoSeReenviaNiSeReemplazaDosVeces() {
        respuestaDeSunat("RECHAZADO", 2800);
        assertThatThrownBy(() -> envioSunat.reenviarComprobante(comprobanteId))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("reemplazo");

        facturacion.emitirReemplazo(comprobanteId);
        em.flush();
        em.clear();
        assertThatThrownBy(() -> facturacion.emitirReemplazo(comprobanteId))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("ya fue reemplazado");
        assertThat(contar("SELECT COUNT(*) FROM comprobantes WHERE venta_id = ?")).isEqualTo(2);
    }

    @Test
    void unaVentaAnuladaNoNecesitaReemplazo() {
        respuestaDeSunat("RECHAZADO", 2800);
        jdbc.update("UPDATE ventas SET estado = 'ANULADA', fecha_anulacion = SYSDATE WHERE id = ?", ventaId);
        em.clear();
        assertThatThrownBy(() -> facturacion.emitirReemplazo(comprobanteId))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("anulada");
    }

    /** Aunque se salte el servicio, Oracle no admite dos comprobantes vigentes para la misma venta. */
    @Test
    void oracleAdmiteUnSoloComprobanteVigentePorVenta() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO comprobantes (serie, numero, fecha_emision, tipo, total, venta_id, estado_sunat)
                VALUES ('B999', '00000001', SYSDATE, 'BOLETA', 1, ?, 'PENDIENTE')""", ventaId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** Editar el cliente o renombrar el producto después no cambia lo emitido (D-5). */
    @Test
    void elReceptorYLaDescripcionQuedanComoSeEmitieron() {
        Comprobante emitido = comprobantes.findById(comprobanteId).orElseThrow();
        String nombre = emitido.getReceptorNombre();
        assertThat(nombre).isNotBlank();
        assertThat(emitido.getReceptorNumDoc()).isNotBlank();

        jdbc.update("UPDATE clientes SET nombreorazon_social = 'Nombre editado después' WHERE id = ?", clienteId);
        jdbc.update("UPDATE productos SET nombre = 'Producto renombrado' WHERE id = ?", equipoId);
        em.clear();

        Comprobante despues = comprobantes.findById(comprobanteId).orElseThrow();
        assertThat(despues.getReceptorNombre()).isEqualTo(nombre);
        assertThat(despues.getVenta().getDetalles()).singleElement()
                .satisfies(d -> assertThat(d.getDescripcion()).isEqualTo("Split de prueba (reemplazo)"));
    }

    /**
     * Plazo de envío (D-6): un pendiente emitido hace 4 días ya no se envía ni se reenvía; se reemplaza por
     * otro con fecha de hoy. La campana lo cuenta mientras tanto.
     */
    @Test
    void unPendienteFueraDePlazoSeReemplazaYLaCampanaLoAvisa() {
        String numero = jdbc.queryForObject("SELECT serie || '-' || TO_NUMBER(numero) FROM comprobantes WHERE id = ?", String.class, comprobanteId);
        jdbc.update("UPDATE comprobantes SET estado_sunat = 'PENDIENTE', sunat_codigo = NULL, fecha_emision = ? WHERE id = ?", java.sql.Date.valueOf(java.time.LocalDate.now().minusDays(2)), comprobanteId);
        em.clear();
        assertThat(facturacion.plazoDeEnvio().numerosPorVencer()).contains(numero);
        assertThat(facturacion.estadosDeVentas(List.of(ventaId)).get(0).comprobante().puedeReemplazar()).isFalse();
        assertThatThrownBy(() -> facturacion.emitirReemplazo(comprobanteId)).isInstanceOf(ResponseStatusException.class);

        jdbc.update("UPDATE comprobantes SET fecha_emision = ? WHERE id = ?", java.sql.Date.valueOf(java.time.LocalDate.now().minusDays(4)), comprobanteId);
        em.clear();
        assertThat(facturacion.plazoDeEnvio().numerosFueraDePlazo()).contains(numero);
        EstadoSunatVentaDTO.Documento doc = facturacion.estadosDeVentas(List.of(ventaId)).get(0).comprobante();
        assertThat(doc.fueraDePlazo()).isTrue();
        assertThat(doc.puedeReemplazar()).isTrue();
        assertThat(doc.fechaLimiteEnvio()).isEqualTo(java.time.LocalDate.now().minusDays(1));
        assertThatThrownBy(() -> envioSunat.reenviarComprobante(comprobanteId))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("venció el plazo");

        Comprobante nuevo = facturacion.emitirReemplazo(comprobanteId);
        em.flush();
        assertThat(nuevo.getFechaEmision()).isEqualTo(java.time.LocalDate.now());
        assertThat(nuevo.getReemplazaA().getId()).isEqualTo(comprobanteId);
        assertThat(facturacion.plazoDeEnvio().numerosFueraDePlazo()).doesNotContain(numero);
    }

    /** Excepción reenviada: sale con el dato corregido del cliente (SUNAT no había registrado el comprobante). */
    @Test
    void unaExcepcionReenviadaTomaElDatoCorregido() {
        respuestaDeSunat("RECHAZADO", 305);
        jdbc.update("UPDATE clientes SET nombreorazon_social = 'Nombre corregido' WHERE id = ?", clienteId);
        em.clear();

        envioSunat.reenviarComprobante(comprobanteId);
        em.flush();
        em.clear();

        Comprobante c = comprobantes.findById(comprobanteId).orElseThrow();
        assertThat(c.getReceptorNombre()).isEqualTo("Nombre corregido");
        assertThat(c.getEstadoSunat()).isEqualTo(EstadoSunat.PENDIENTE);
    }
}
