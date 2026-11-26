package com.bomerp.servicios;
import com.bomerp.seguridad.permisos.KeycloakAdminClient;
import com.bomerp.servicios.recordatorio.RecordatorioRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Solo lectura: valida el mapeo real del esquema migrado, sin activar servidores ni tareas de envío. */
@Tag("oracle")
@SpringBootTest(properties={"spring.jpa.hibernate.ddl-auto=validate","spring.mail.username=","app.facturacion.modo=simulado",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:1/keys","app.recordatorios.cron=-"})
class MigracionReunionOracleTest {
    @Autowired RecordatorioRepository recordatorios;@Autowired JdbcTemplate jdbc;
    @MockitoBean KeycloakAdminClient keycloak;
    @Test void esquemaRealValidaYPermiteConsultarRecordatoriosYAmbienteDeNotas() {
        assertThat(recordatorios.count()).isGreaterThanOrEqualTo(0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_tab_columns WHERE table_name='NOTAS_CREDITO' AND column_name='AMBIENTE_EMISION'",Integer.class)).isEqualTo(1);
    }
}
