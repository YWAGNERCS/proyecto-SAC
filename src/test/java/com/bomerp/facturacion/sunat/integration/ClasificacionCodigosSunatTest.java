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
            "1033, ACEPTADO",       // ya registrado en un envío anterior
            "4252, OBSERVADO",      // aceptado con observaciones
            "2800, RECHAZADO",      // rechazo por datos del comprobante
            "3105, RECHAZADO",
            "109, REINTENTAR",      // falla del sistema de SUNAT
            "130, REINTENTAR",
            "-1, REINTENTAR",       // sin código legible
            "111, REINTENTAR",
            "305, RECHAZADO",       // excepción por datos o configuración
    })
    void clasifica(int codigo, Resultado esperado) {
        assertThat(SunatDirectoClient.clasificar(codigo)).isEqualTo(esperado);
    }
}
