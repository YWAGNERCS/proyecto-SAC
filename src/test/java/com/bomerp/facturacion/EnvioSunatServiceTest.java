package com.bomerp.facturacion;

import com.bomerp.facturacion.comprobante.entity.Comprobante;
import com.bomerp.facturacion.comprobante.entity.Comprobante.EstadoSunat;
import com.bomerp.facturacion.comprobante.repository.ComprobanteRepository;
import com.bomerp.facturacion.facturacion.service.EnvioSunatService;
import com.bomerp.facturacion.notacredito.entity.NotaCredito;
import com.bomerp.facturacion.notacredito.repository.NotaCreditoRepository;
import com.bomerp.facturacion.ose.integration.OseClient;
import com.bomerp.facturacion.ose.integration.OseClient.RespuestaOse;
import com.bomerp.facturacion.ose.integration.OseClient.Resultado;
import com.bomerp.ventas.venta.entity.Venta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Envío a SUNAT en segundo plano: qué estado queda según lo que responde SUNAT. */
class EnvioSunatServiceTest {

    private ComprobanteRepository comprobantes;
    private NotaCreditoRepository notas;
    private OseClient ose;
    private ApplicationEventPublisher eventos;
    private EnvioSunatService servicio;

    @BeforeEach
    void preparar() {
        comprobantes = mock(ComprobanteRepository.class);
        notas = mock(NotaCreditoRepository.class);
        ose = mock(OseClient.class);
        eventos = mock(ApplicationEventPublisher.class);
        servicio = new EnvioSunatService(comprobantes, notas, ose, eventos, mock(PlatformTransactionManager.class), 0);
    }

    private Comprobante comprobante(long id, EstadoSunat estado) {
        Comprobante c = new Comprobante();
        c.setId(id);
        c.setSerie("F001");
        c.setNumero("00000001");
        c.setEstadoSunat(estado);
        when(comprobantes.findById(id)).thenReturn(Optional.of(c));
        return c;
    }

    private static RespuestaOse respuesta(Resultado r, Integer codigo, String mensaje) {
        return new RespuestaOse(r, codigo, mensaje, null, null, new byte[]{1}, r == Resultado.REINTENTAR ? null : new byte[]{2});
    }

    @Test
    void aceptadoGuardaCodigoXmlYCdrYAvisaParaElCorreo() {
        Comprobante c = comprobante(1, EstadoSunat.PENDIENTE);
        when(ose.emitir(c)).thenReturn(respuesta(Resultado.ACEPTADO, 0, "La Factura numero F001-1, ha sido aceptada"));

        assertThat(servicio.enviarComprobante(1L)).isEqualTo(EstadoSunat.ACEPTADO);
        assertThat(c.getEnvio().getCodigo()).isZero();
        assertThat(c.getEnvio().getIntentos()).isEqualTo(1);
        assertThat(c.getEnvio().getXmlFirmado()).isNotNull();
        assertThat(c.getEnvio().getCdr()).isNotNull();
        verify(eventos).publishEvent(new EnvioSunatService.ComprobanteAceptadoEvent(1L));
    }

    @Test
    void observadoEsValidoYConservaLaObservacion() {
        Comprobante c = comprobante(2, EstadoSunat.PENDIENTE);
        when(ose.emitir(c)).thenReturn(respuesta(Resultado.OBSERVADO, 4252, "El dato ingresado como atributo @listName es incorrecto"));

        assertThat(servicio.enviarComprobante(2L)).isEqualTo(EstadoSunat.OBSERVADO);
        assertThat(c.getEnvio().getMensaje()).contains("listName");
        verify(eventos).publishEvent(any(EnvioSunatService.ComprobanteAceptadoEvent.class));
    }

    @Test
    void rechazadoGuardaElMotivoYNoSeEnviaPorCorreo() {
        Comprobante c = comprobante(3, EstadoSunat.PENDIENTE);
        when(ose.emitir(c)).thenReturn(respuesta(Resultado.RECHAZADO, 2800, "El dato ingresado en el tipo de documento de identidad del receptor no esta permitido."));

        assertThat(servicio.enviarComprobante(3L)).isEqualTo(EstadoSunat.RECHAZADO);
        assertThat(c.getEnvio().getCodigo()).isEqualTo(2800);
        verifyNoInteractions(eventos);
    }

    @Test
    void sunatSinResponderQuedaPendienteYCuentaElIntento() {
        Comprobante c = comprobante(4, EstadoSunat.PENDIENTE);
        when(ose.emitir(c)).thenReturn(RespuestaOse.reintentar("SUNAT respondió HTTP 401: Authorization Required", new byte[]{1}));

        assertThat(servicio.enviarComprobante(4L)).isEqualTo(EstadoSunat.PENDIENTE);
        assertThat(c.getEnvio().getIntentos()).isEqualTo(1);
        assertThat(c.getEnvio().getMensaje()).contains("401");
        verifyNoInteractions(eventos);
    }

    @Test
    void unComprobanteYaAceptadoNoSeReenvia() {
        comprobante(5, EstadoSunat.ACEPTADO);
        assertThat(servicio.enviarComprobante(5L)).isEqualTo(EstadoSunat.ACEPTADO);
        verifyNoInteractions(ose);
    }

    @Test
    void notaDeCreditoEsperaAQueSunatAcepteElOriginal() {
        NotaCredito n = nota(10, comprobante(6, EstadoSunat.PENDIENTE));
        assertThat(servicio.enviarNota(10L)).isEqualTo(EstadoSunat.PENDIENTE);
        assertThat(n.getEnvio().getIntentos()).isZero();
        verify(ose, never()).emitirNotaCredito(any());
    }

    @Test
    void notaDeCreditoDeUnOriginalRechazadoNoSeEnvia() {
        NotaCredito n = nota(11, comprobante(7, EstadoSunat.RECHAZADO));
        assertThat(servicio.enviarNota(11L)).isEqualTo(EstadoSunat.RECHAZADO);
        assertThat(n.getEnvio().getMensaje()).contains("rechazado");
        verify(ose, never()).emitirNotaCredito(any());
    }

    @Test
    void notaDeCreditoDeUnOriginalAceptadoSeEnvia() {
        NotaCredito n = nota(12, comprobante(8, EstadoSunat.ACEPTADO));
        when(ose.emitirNotaCredito(n)).thenReturn(respuesta(Resultado.ACEPTADO, 0, "La Nota de Credito numero FC01-1, ha sido aceptada"));
        assertThat(servicio.enviarNota(12L)).isEqualTo(EstadoSunat.ACEPTADO);
    }

    @Test
    void elReintentoPideALaBaseSoloLosQueNoAgotaronSusIntentos() {
        // El filtro va en la consulta (F-02); que Oracle lo cumpla lo prueba ReintentoSunatIntegracionTest
        when(comprobantes.findTop20ByEstadoSunatAndEnvio_IntentosLessThanOrderByIdAsc(EstadoSunat.PENDIENTE, 20)).thenReturn(List.of());
        when(notas.findTop20ByEstadoSunatAndEnvio_IntentosLessThanOrderByIdAsc(EstadoSunat.PENDIENTE, 20)).thenReturn(List.of());

        servicio.reintentarPendientes();
        verify(comprobantes).findTop20ByEstadoSunatAndEnvio_IntentosLessThanOrderByIdAsc(EstadoSunat.PENDIENTE, 20);
        verify(notas).findTop20ByEstadoSunatAndEnvio_IntentosLessThanOrderByIdAsc(EstadoSunat.PENDIENTE, 20);
        verify(ose, after(300).never()).emitir(any());
    }

    @Test
    void reenviarUnRechazadoLoVuelvePendienteConLosIntentosEnCero() {
        Comprobante c = comprobante(13, EstadoSunat.RECHAZADO);
        c.getEnvio().registrarIntento(2800, "Tipo de documento del receptor no permitido", null, null);
        servicio.reenviarComprobante(13L);
        assertThat(c.getEstadoSunat()).isEqualTo(EstadoSunat.PENDIENTE);
        assertThat(c.getEnvio().getIntentos()).isZero();
    }

    @Test
    void noSeReenviaLoQueSunatYaAcepto() {
        comprobante(14, EstadoSunat.ACEPTADO);
        assertThatThrownBy(() -> servicio.reenviarComprobante(14L))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("F001-1 ya fue aceptado");
    }

    private NotaCredito nota(long id, Comprobante original) {
        NotaCredito n = new NotaCredito();
        n.setId(id);
        n.setSerie("FC01");
        n.setNumero("00000001");
        n.setComprobanteReferencia(original);
        when(notas.findById(id)).thenReturn(Optional.of(n));
        return n;
    }

    // --- Protección: una venta anulada no deja su comprobante vigente sin nota de crédito (auditoría M10) ---

    private Comprobante deVenta(Comprobante c, Venta.EstadoVenta estado) {
        Venta v = new Venta();
        v.setId(285L);
        v.setEstado(estado);
        c.setVenta(v);
        return c;
    }

    @Test
    void ventaAnuladaSinNotaPideCrearlaAntesDeEnviar() {
        Comprobante c = deVenta(comprobante(20, EstadoSunat.PENDIENTE), Venta.EstadoVenta.ANULADA);
        // primero no tiene nota; después del evento, Facturación ya la creó
        when(notas.findByComprobanteReferencia_Id(20L)).thenReturn(Optional.empty(), Optional.of(new NotaCredito()));
        when(ose.emitir(c)).thenReturn(respuesta(Resultado.ACEPTADO, 0, "aceptada"));

        assertThat(servicio.enviarComprobante(20L)).isEqualTo(EstadoSunat.ACEPTADO);
        var orden = inOrder(eventos, ose);
        orden.verify(eventos).publishEvent(new EnvioSunatService.VentaAnuladaSinNotaEvent(285L));
        orden.verify(ose).emitir(c);
    }

    @Test
    void ventaAnuladaSinNotaQueNoSePudoCrearNoSeEnvia() {
        Comprobante c = deVenta(comprobante(21, EstadoSunat.PENDIENTE), Venta.EstadoVenta.ANULADA);
        when(notas.findByComprobanteReferencia_Id(21L)).thenReturn(Optional.empty());

        assertThat(servicio.enviarComprobante(21L)).isEqualTo(EstadoSunat.PENDIENTE);
        verify(ose, never()).emitir(any());
        // cuenta como intento (no se reintenta sin fin) y el motivo queda a la vista
        assertThat(c.getEnvio().getIntentos()).isEqualTo(1);
        assertThat(c.getEnvio().getMensaje()).contains("venta #285 está anulada");
    }

    @Test
    void ventaAnuladaQueYaTieneNotaSeEnviaSinCrearOtra() {
        Comprobante c = deVenta(comprobante(22, EstadoSunat.PENDIENTE), Venta.EstadoVenta.ANULADA);
        when(notas.findByComprobanteReferencia_Id(22L)).thenReturn(Optional.of(new NotaCredito()));
        when(ose.emitir(c)).thenReturn(respuesta(Resultado.ACEPTADO, 0, "aceptada"));

        assertThat(servicio.enviarComprobante(22L)).isEqualTo(EstadoSunat.ACEPTADO);
        verify(eventos, never()).publishEvent(any(EnvioSunatService.VentaAnuladaSinNotaEvent.class));
    }

    @Test
    void ventaVigenteNoConsultaNotas() {
        Comprobante c = deVenta(comprobante(23, EstadoSunat.PENDIENTE), Venta.EstadoVenta.REGISTRADA);
        when(ose.emitir(c)).thenReturn(respuesta(Resultado.ACEPTADO, 0, "aceptada"));

        assertThat(servicio.enviarComprobante(23L)).isEqualTo(EstadoSunat.ACEPTADO);
        verify(notas, never()).findByComprobanteReferencia_Id(any());
        verify(eventos, never()).publishEvent(any(EnvioSunatService.VentaAnuladaSinNotaEvent.class));
    }
}
