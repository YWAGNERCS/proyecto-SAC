package com.bomerp.facturacion;

import com.bomerp.facturacion.comprobante.entity.Comprobante;
import com.bomerp.facturacion.comprobante.entity.Comprobante.EstadoSunat;
import com.bomerp.facturacion.comprobante.repository.ComprobanteRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Revisión externa F-02, contra Oracle: el reintento automático no se traba con comprobantes agotados.
 *
 * Antes se pedían los 20 primeros PENDIENTES y después se descartaban los que tenían 20 intentos: con 20
 * agotados al principio, los siguientes no se reintentaban nunca. Se arma ese caso con comprobantes que ya
 * existen (todo se deshace al terminar; el reintento automático queda apagado para no tocar los reales).
 */
@SpringBootTest(properties = "app.facturacion.reintento-inicial-ms=86400000")
@Transactional
class ReintentoSunatIntegracionTest {

    @Autowired private ComprobanteRepository comprobantes;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;

    @Test
    void veinticincoAgotadosAlPrincipioNoTapanAlSiguiente() {
        List<Long> ids = jdbc.queryForList("SELECT id FROM comprobantes ORDER BY id FETCH FIRST 26 ROWS ONLY", Long.class);
        assertThat(ids).hasSize(26);
        List<Long> agotados = ids.subList(0, 25);
        Long reintentable = ids.get(25);
        for (Long id : agotados) {
            jdbc.update("UPDATE comprobantes SET estado_sunat = 'PENDIENTE', sunat_intentos = 20 WHERE id = ?", id);
        }
        jdbc.update("UPDATE comprobantes SET estado_sunat = 'PENDIENTE', sunat_intentos = 3 WHERE id = ?", reintentable);
        em.clear();

        List<Long> tomados = comprobantes
                .findTop20ByEstadoSunatAndEnvio_IntentosLessThanOrderByIdAsc(EstadoSunat.PENDIENTE, 20)
                .stream().map(Comprobante::getId).toList();

        assertThat(tomados).contains(reintentable).doesNotContainAnyElementsOf(agotados);
    }
}
