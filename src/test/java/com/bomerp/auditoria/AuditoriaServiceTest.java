package com.bomerp.auditoria;

import com.bomerp.auditoria.entity.RegistroCambio;
import com.bomerp.auditoria.repository.RegistroCambioRepository;
import com.bomerp.auditoria.service.AuditoriaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** RF-29: cada intento de cambio se guarda en palabras y con su resultado. */
@ExtendWith(MockitoExtension.class)
class AuditoriaServiceTest {

    @Mock RegistroCambioRepository repository;
    @InjectMocks AuditoriaService service;

    private RegistroCambio registrar(String metodo, String ruta, int estado, String idCreado) {
        service.registrar("lucy", "ASISTENTE", metodo, ruta, estado, idCreado, null, "127.0.0.1");
        ArgumentCaptor<RegistroCambio> captor = ArgumentCaptor.forClass(RegistroCambio.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void unaAnulacionHechaSeDescribeEnPasado() {
        RegistroCambio r = registrar("PATCH", "/api/v1/admin/ventas/113/anular", 200, null);

        assertThat(r.getAccion()).isEqualTo("Anuló la venta #113");
        assertThat(r.getModulo()).isEqualTo("Ventas");
        assertThat(r.getResultado()).isEqualTo(RegistroCambio.Resultado.EXITO);
    }

    @Test
    void unIntentoSinPermisoQuedaComoIntento() {
        RegistroCambio r = registrar("DELETE", "/api/v1/admin/clientes/5", 403, null);

        assertThat(r.getAccion()).isEqualTo("Intentó eliminar el cliente #5");
        assertThat(r.getResultado()).isEqualTo(RegistroCambio.Resultado.DENEGADO);
    }

    @Test
    void loCreadoLlevaSuNumero() {
        RegistroCambio r = registrar("POST", "/api/v1/admin/cuentas-por-pagar/pagos", 201, "12");

        assertThat(r.getAccion()).isEqualTo("Registró el pago a proveedor #12");
        assertThat(r.getModulo()).isEqualTo("Compras");
    }

    @Test
    void unRechazoNoMuestraUnNumeroInventado() {
        RegistroCambio r = registrar("POST", "/api/v1/admin/inventario/ajustes", 409, null);

        assertThat(r.getAccion()).isEqualTo("Intentó ajustar el inventario (ajuste)");
        assertThat(r.getResultado()).isEqualTo(RegistroCambio.Resultado.RECHAZADO);
    }

    @Test
    void unaRutaSinReglaUsaElModuloGenerico() {
        RegistroCambio r = registrar("PUT", "/api/v1/admin/tipo-cambio/hoy", 500, null);

        assertThat(r.getModulo()).isEqualTo("Tipo de cambio");
        assertThat(r.getResultado()).isEqualTo(RegistroCambio.Resultado.ERROR);
    }

    @Test
    void siNoSePuedeGuardarNoTumbaLaOperacion() {
        when(repository.save(any())).thenThrow(new IllegalStateException("base caída"));

        assertThatCode(() -> service.registrar("lucy", null, "POST", "/api/v1/admin/compras", 201, "1", null, null))
                .doesNotThrowAnyException();
    }
}
