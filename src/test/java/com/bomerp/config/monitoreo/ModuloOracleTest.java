package com.bomerp.config.monitoreo;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.sql.Connection;
import java.sql.SQLClientInfoException;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** BD2 S14: cada conexión dice a Oracle qué operación del sistema la usa. */
class ModuloOracleTest {

    private final ModuloOracle.Filtro filtro = new ModuloOracle.Filtro();

    /** Atiende la petición y marca una conexión "dentro" de ella, como lo haría un servicio o la seguridad. */
    private Connection atender(String metodo, String uri) throws Exception {
        Connection conexion = mock(Connection.class);
        MockHttpServletRequest peticion = new MockHttpServletRequest(metodo, uri);
        filtro.doFilter(peticion, new MockHttpServletResponse(), (req, res) -> ModuloOracle.marcar(conexion));
        return conexion;
    }

    @Test
    void laAnulacionDeUnaVentaSeVeComoModuloVentasConSuId() throws Exception {
        Connection conexion = atender("PATCH", "/api/v1/admin/ventas/182/anular");
        verify(conexion).setClientInfo("OCSID.MODULE", "SGE-JD ventas");
        verify(conexion).setClientInfo("OCSID.ACTION", "PATCH /ventas/182/anular");
    }

    @Test
    void elCatalogoPublicoTambienSeMarca() throws Exception {
        Connection conexion = atender("GET", "/api/v1/publico/catalogo");
        verify(conexion).setClientInfo("OCSID.MODULE", "SGE-JD publico");
    }

    @Test
    void laAccionSeRecortaAlLimiteDeOracle() throws Exception {
        Connection conexion = atender("POST", "/api/v1/admin/cobranza/devoluciones/15/registrar-con-nombre-largo");
        verify(conexion).setClientInfo("OCSID.MODULE", "SGE-JD cobranza");
        verify(conexion).setClientInfo("OCSID.ACTION", "POST /cobranza/devoluciones/15/r");   // 32 caracteres
    }

    @Test
    void alTerminarLaPeticionElHiloQuedaLimpio() throws Exception {
        atender("GET", "/api/v1/admin/ventas");
        Connection despues = mock(Connection.class);
        ModuloOracle.marcar(despues);
        verify(despues).setClientInfo("OCSID.MODULE", "SGE-JD tareas");
    }

    @Test
    void unMotorQueNoConoceLasClavesNoRompeNada() throws Exception {
        Connection conexion = mock(Connection.class);
        doThrow(new SQLClientInfoException()).when(conexion).setClientInfo(anyString(), anyString());
        AtomicReference<Connection> devuelta = new AtomicReference<>(ModuloOracle.marcar(conexion));   // no lanza (H2)
        verify(conexion).setClientInfo("OCSID.MODULE", "SGE-JD tareas");
        assert devuelta.get() == conexion;
    }
}
