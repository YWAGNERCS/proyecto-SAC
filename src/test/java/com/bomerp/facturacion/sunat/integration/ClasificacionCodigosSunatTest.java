package com.bomerp.facturacion.sunat.integration;

import com.bomerp.facturacion.ose.integration.OseClient.Resultado;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Qué hace SGE-JD con cada código de respuesta de SUNAT. */
class ClasificacionCodigosSunatTest {

    @ParameterizedTest(name = "código {0} → {1}")
    @CsvSource({
            "0, ACEPTADO",          // aceptado
            "1033, POR_CONFIRMAR",  // ya registrado por un envío anterior: no se sabe si fue aceptado
            "4252, OBSERVADO",      // aceptado con observaciones
            "2800, RECHAZADO",      // rechazo por datos del comprobante
            "3105, RECHAZADO",
            "109, REINTENTAR",      // falla del sistema de SUNAT
            "130, REINTENTAR",
            "-1, REINTENTAR",       // sin código legible
            "111, REINTENTAR",
            "305, RECHAZADO",       // excepción por datos o configuración
            "101, CREDENCIALES",    // encabezado de seguridad incorrecto
            "102, CREDENCIALES",    // usuario o contraseña incorrectos
            "104, CREDENCIALES",    // clave incorrecta
            "106, CREDENCIALES",    // usuario no válido
            "100, REINTENTAR",      // sistema de SUNAT sin responder
    })
    void clasifica(int codigo, Resultado esperado) {
        assertThat(SunatDirectoClient.clasificar(codigo)).isEqualTo(esperado);
    }

    /** El código de un rechazo SOAP sale de faultcode, no del texto (que trae el número de ticket). */
    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "soap-env:Client.2800, 2800",
            "soap-env:Client.0111, 111",
            "soap-env:Client.1033, 1033",
            "soap-env:Server, -1",
            "Client.abc, -1",
    })
    void codigoDelFaultcode(String faultcode, int esperado) {
        assertThat(SunatSoapClient.codigoDeFalla(faultcode)).isEqualTo(esperado);
    }
}
