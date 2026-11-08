package com.bomerp.facturacion.sunat.integration;

import com.bomerp.config.empresa.EmpresaProperties;
import com.bomerp.facturacion.comprobante.entity.Comprobante.AmbienteEmision;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El ambiente de SUNAT se DECLARA (SUNAT_AMBIENTE) y se comprueba contra la dirección oficial; ya no se deduce buscando
 * "beta" en la URL, que clasificaba como PRODUCCION cualquier otra dirección (un intermediario, un proxy, un error).
 * Nada de esto se conecta a SUNAT: solo se construye y valida la configuración.
 */
class AmbienteSunatTest {

    private static final String PROD = AmbienteSunat.URL_PRODUCCION;
    private static final String BETA = AmbienteSunat.URL_BETA;

    @Test
    void lasDireccionesOficialesConSuAmbienteSeAceptan() {
        assertThat(AmbienteSunat.validar("PRODUCCION", PROD)).isEqualTo(AmbienteEmision.PRODUCCION);
        assertThat(AmbienteSunat.validar("BETA", BETA)).isEqualTo(AmbienteEmision.BETA);
        // Variantes inocuas: minúsculas, tilde, espacios, mayúsculas en el servidor, "/" final, puerto 443 explícito
        assertThat(AmbienteSunat.validar(" producción ", "  " + PROD + "  ")).isEqualTo(AmbienteEmision.PRODUCCION);
        assertThat(AmbienteSunat.validar("beta", "https://E-BETA.SUNAT.GOB.PE/ol-ti-itcpfegem-beta/billService/"))
                .isEqualTo(AmbienteEmision.BETA);
        assertThat(AmbienteSunat.validar("PRODUCCION", "https://e-factura.sunat.gob.pe:443/ol-ti-itcpfegem/billService"))
                .isEqualTo(AmbienteEmision.PRODUCCION);
    }

    @Test
    void elAmbienteEsObligatorioYSoloPuedeSerProduccionOBeta() {
        assertThatThrownBy(() -> AmbienteSunat.validar(null, PROD)).hasMessageContaining("Falta SUNAT_AMBIENTE");
        assertThatThrownBy(() -> AmbienteSunat.validar("  ", BETA)).hasMessageContaining("Falta SUNAT_AMBIENTE");
        assertThatThrownBy(() -> AmbienteSunat.validar("PRUEBAS", BETA)).hasMessageContaining("tiene que ser PRODUCCION o BETA");
        assertThatThrownBy(() -> AmbienteSunat.validar("BETA", null)).hasMessageContaining("Falta SUNAT_URL");
    }

    @Test
    void elAmbienteTieneQueCorresponderALaDireccion() {
        assertThatThrownBy(() -> AmbienteSunat.validar("PRODUCCION", BETA))
                .hasMessageContaining("SUNAT_AMBIENTE=PRODUCCION pero SUNAT_URL es la dirección de BETA");
        assertThatThrownBy(() -> AmbienteSunat.validar("BETA", PROD))
                .hasMessageContaining("SUNAT_AMBIENTE=BETA pero SUNAT_URL es la dirección de PRODUCCION");
    }

    @Test
    void seBloqueanIntermediariosYDireccionesNoOficiales() {
        String[] noOficiales = {
                "http://127.0.0.1:9081/billService",                                   // el intermediario de la prueba del 08/11
                "http://e-factura.sunat.gob.pe/ol-ti-itcpfegem/billService",           // sin https
                "https://e-factura.sunat.gob.pe.otro-dominio.com/ol-ti-itcpfegem/billService",   // imitación del servidor
                "https://otro@e-factura.sunat.gob.pe/ol-ti-itcpfegem/billService",     // usuario delante del servidor
                "https://e-factura.sunat.gob.pe:8443/ol-ti-itcpfegem/billService",     // otro puerto
                "https://e-factura.sunat.gob.pe/ol-ti-itcpfegem/billService?destino=x",   // parámetros
                "https://e-factura.sunat.gob.pe/otra-ruta/billService",                // otra ruta
                "https://proxy.empresa.pe/sunat/produccion",                           // proxy de la red
                "no es una url"};
        for (String url : noOficiales) {
            assertThatThrownBy(() -> AmbienteSunat.validar("PRODUCCION", url))
                    .as(url).hasMessageContaining("no es la dirección oficial de SUNAT");
        }
        // Lo que antes pasaba como PRODUCCION por no decir "beta" ahora no se acepta ni como BETA
        assertThatThrownBy(() -> AmbienteSunat.validar("BETA", "http://127.0.0.1:9081/billService"))
                .hasMessageContaining("no es la dirección oficial de SUNAT");
    }

    @Test
    void elClienteNoArrancaConUnaConfiguracionInvalidaYUsaElAmbienteDeclarado() {
        EmpresaProperties empresa = new EmpresaProperties("JD Refrigeración S.A.C.", "20000000001", "Lima", null);
        assertThatThrownBy(() -> new SunatDirectoClient(
                new SunatProperties("PRODUCCION", "http://127.0.0.1:9081/billService", "U", "x", null, null), empresa))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no es la dirección oficial");
        assertThatThrownBy(() -> new SunatDirectoClient(new SunatProperties(null, BETA, "MODDATOS", "x", null, null), empresa))
                .hasMessageContaining("Falta SUNAT_AMBIENTE");

        SunatDirectoClient beta = new SunatDirectoClient(new SunatProperties("BETA", BETA, "MODDATOS", "x", null, null), empresa);
        assertThat(beta.ambiente()).isEqualTo(AmbienteEmision.BETA);
        assertThat(beta.conValidezTributaria()).isFalse();
        SunatDirectoClient prod = new SunatDirectoClient(new SunatProperties("PRODUCCION", PROD, "U", "x", null, null), empresa);
        assertThat(prod.ambiente()).isEqualTo(AmbienteEmision.PRODUCCION);
        assertThat(prod.conValidezTributaria()).isTrue();
    }
}
