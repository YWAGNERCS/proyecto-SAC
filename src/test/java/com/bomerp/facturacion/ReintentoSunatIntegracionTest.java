package com.bomerp.facturacion;

import com.bomerp.facturacion.comprobante.entity.Comprobante;
import com.bomerp.facturacion.comprobante.entity.Comprobante.EstadoSunat;
import com.bomerp.facturacion.comprobante.repository.ComprobanteRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import org.springframework.data.domain.Limit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Revisión externa F-02, contra Oracle: el reintento automático no se traba con comprobantes agotados.
 *
 * Antes se pedían los 20 primeros PENDIENTES y después se descartaban los que tenían 20 intentos: con 20
 * agotados al principio, los siguientes no se reintentaban nunca. Se arma ese caso con comprobantes que ya
 * existen (todo se deshace al terminar; el reintento automático queda apagado para no tocar los reales).
 * Tampoco toma uno por confirmar (1033) ni uno que pasó el plazo de envío de 3 días (informe D-6); el
 * emitido hace justo 3 días todavía está en plazo.
 */
// Necesita Oracle con los datos de desarrollo: la integración continua la salta (-DexcludedGroups=oracle)
@Tag("oracle")
@SpringBootTest(properties = "app.facturacion.reintento-inicial-ms=86400000")
@Transactional
class ReintentoSunatIntegracionTest {

    @Autowired private ComprobanteRepository comprobantes;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;

    @Test
    void veinticincoAgotadosAlPrincipioNoTapanAlSiguiente() {
        List<Long> ids = jdbc.queryForList("SELECT id FROM comprobantes ORDER BY id FETCH FIRST 28 ROWS ONLY", Long.class);
        assertThat(ids).hasSize(28);
        List<Long> agotados = ids.subList(0, 25);
        Long reintentable = ids.get(25);
        Long porConfirmar = ids.get(26);
        Long fueraDePlazo = ids.get(27);
        for (Long id : agotados) {
            jdbc.update("UPDATE comprobantes SET estado_sunat = 'PENDIENTE', sunat_intentos = 20, fecha_emision = ? WHERE id = ?", java.sql.Date.valueOf(java.time.LocalDate.now().minusDays(0)), id);
        }
        jdbc.update("UPDATE comprobantes SET estado_sunat = 'PENDIENTE', sunat_intentos = 3, sunat_codigo = NULL, fecha_emision = ? WHERE id = ?", java.sql.Date.valueOf(java.time.LocalDate.now().minusDays(3)), reintentable);
        // 1033: SUNAT ya tenía el número; reenviarlo solo repetiría la respuesta, así que no se toma
        jdbc.update("UPDATE comprobantes SET estado_sunat = 'PENDIENTE', sunat_intentos = 1, sunat_codigo = 1033, fecha_emision = ? WHERE id = ?", java.sql.Date.valueOf(java.time.LocalDate.now().minusDays(0)), porConfirmar);
        // Emitido hace 4 días: pasó el plazo de 3 (D-6); enviarlo ahora ya no le daría validez
        jdbc.update("UPDATE comprobantes SET estado_sunat = 'PENDIENTE', sunat_intentos = 0, sunat_codigo = NULL, fecha_emision = ? WHERE id = ?", java.sql.Date.valueOf(java.time.LocalDate.now().minusDays(4)), fueraDePlazo);
        em.clear();

        List<Long> tomados = comprobantes
                .pendientesParaReintentar(EstadoSunat.PENDIENTE, 20, 1033, java.time.LocalDate.now().minusDays(Comprobante.DIAS_PLAZO_ENVIO), Limit.of(20))
                .stream().map(Comprobante::getId).toList();

        assertThat(tomados).hasSizeLessThanOrEqualTo(20)
                .contains(reintentable)
                .doesNotContain(porConfirmar, fueraDePlazo)
                .doesNotContainAnyElementsOf(agotados);
    }
}
