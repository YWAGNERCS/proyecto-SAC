package com.bomerp.proformas.proforma.service;

import com.bomerp.clientes.general.integration.Cliente;
import com.bomerp.notificaciones.notificacion.service.NotificadorCorreo;
import com.bomerp.notificaciones.notificacion.service.NotificadorWhatsApp;
import com.bomerp.proformas.pdf.service.GeneradorPdfService;
import com.bomerp.proformas.proforma.entity.Proforma;
import com.bomerp.proformas.proforma.repository.ProformaRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProformaEnvioWhatsAppTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void laSimulacionNoSeGuardaComoEnvioAunqueSalgaElCorreo(boolean porCorreo) throws Exception {
        var repositorio = mock(ProformaRepository.class);
        var whatsapp = mock(NotificadorWhatsApp.class);
        var correo = mock(NotificadorCorreo.class);
        var pdf = mock(GeneradorPdfService.class);
        var transacciones = mock(PlatformTransactionManager.class);
        when(transacciones.getTransaction(any())).thenAnswer(invocacion -> new SimpleTransactionStatus());
        var servicio = new ProformaService(repositorio, null, null, whatsapp, correo, pdf,
                null, null, null, transacciones, null);

        var cliente = new Cliente();
        cliente.setNombreORazonSocial("Cliente de prueba");
        cliente.setTelefono("999999999");
        cliente.setCorreo("compras@example.com");
        var proforma = new Proforma();
        proforma.setId(1L);
        proforma.setCliente(cliente);
        proforma.setMoneda(Proforma.Moneda.SOLES);
        when(repositorio.findById(1L)).thenReturn(Optional.of(proforma));
        when(pdf.generarProformaPdf(proforma)).thenReturn(new byte[] {1});

        var resultado = servicio.enviar(1L, true, porCorreo);

        assertThat(resultado.whatsapp()).isEqualTo("SIMULADO");
        assertThat(resultado.mensaje()).contains("simulado, sin entrega al cliente");
        assertThat(resultado.correo()).isEqualTo(porCorreo ? "ENVIADO" : "NO_SOLICITADO");
        assertThat(proforma.isEnviadoWhatsApp()).isFalse();
        assertThat(proforma.isEnviadoCorreo()).isEqualTo(porCorreo);
        verify(whatsapp).enviarWhatsApp(eq(cliente.getTelefono()), any());
        verify(correo, times(porCorreo ? 1 : 0)).enviarConAdjuntos(any());
        verify(repositorio, times(porCorreo ? 1 : 0)).save(proforma);
    }
}
