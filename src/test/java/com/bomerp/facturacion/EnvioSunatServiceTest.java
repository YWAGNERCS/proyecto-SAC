package com.bomerp.facturacion;

import com.bomerp.facturacion.comprobante.entity.Comprobante;
import com.bomerp.facturacion.comprobante.entity.Comprobante.EstadoSunat;
import com.bomerp.facturacion.comprobante.entity.EnvioSunatRegistro;
import com.bomerp.facturacion.comprobante.repository.ComprobanteRepository;
import com.bomerp.facturacion.comprobante.repository.EnvioSunatRegistroRepository;
import com.bomerp.facturacion.facturacion.service.EnvioSunatService;
import com.bomerp.facturacion.notacredito.entity.NotaCredito;
import com.bomerp.facturacion.notacredito.repository.NotaCreditoRepository;
import com.bomerp.facturacion.ose.integration.OseClient;
import com.bomerp.facturacion.ose.integration.OseClient.RespuestaOse;
import com.bomerp.facturacion.ose.integration.OseClient.Resultado;
import com.bomerp.ventas.venta.entity.Venta;
import org.junit.jupiter.api.AfterEach;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Envío a SUNAT en segundo plano: qué estado queda según lo que responde SUNAT. */
class EnvioSunatServiceTest {

    private ComprobanteRepository comprobantes;
    private NotaCreditoRepository notas;
    private EnvioSunatRegistroRepository registros;
    private OseClient ose;
    private ApplicationEventPublisher eventos;
    private EnvioSunatService servicio;

    @BeforeEach
    void preparar() {
        comprobantes = mock(ComprobanteRepository.class);
        notas = mock(NotaCreditoRepository.class);
        registros = mock(EnvioSunatRegistroRepository.class);
        ose = mock(OseClient.class);
        eventos = mock(ApplicationEventPublisher.class);
        servicio = new EnvioSunatService(comprobantes, notas, registros, ose, eventos, mock(PlatformTransactionManager.class), 0);
    }

    /**
     * Auditoría B10: cada prueba espera a que su cola de envíos termine. Antes, un envío que dejaba en cola una
     * prueba (p. ej. un reenvío) corría después de ella, contra mocks sin respuesta, y escribía un
     * NullPointerException en el registro: una carrera entre pruebas que podía volverlas inestables.
     */
    @AfterEach
    void esperarLaCola() throws InterruptedException {
        servicio.detener();
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

    /** Cada respuesta de SUNAT queda en el historial con su propio XML y CDR (informe de rechazos SUNAT, D-3). */
    @Test
    void cadaRespuestaQuedaEnElHistorialSinPisarLaAnterior() {
        Comprobante c = comprobante(30, EstadoSunat.PENDIENTE);
        when(ose.emitir(c)).thenReturn(
                RespuestaOse.reintentar("SUNAT no respondió", new byte[]{1}),
                respuesta(Resultado.RECHAZADO, 2800, "Tipo de documento del receptor no permitido"));

        servicio.enviarComprobante(30L);
        servicio.enviarComprobante(30L);

        var guardados = org.mockito.ArgumentCaptor.forClass(EnvioSunatRegistro.class);
        verify(registros, times(2)).save(guardados.capture());
        var primero = guardados.getAllValues().get(0);
        var segundo = guardados.getAllValues().get(1);
        assertThat(primero.getComprobanteId()).isEqualTo(30L);
        assertThat(primero.getResultado()).isEqualTo("REINTENTAR");
        assertThat(primero.getIntento()).isEqualTo(1);
        assertThat(primero.tieneCdr()).isFalse();
        assertThat(segundo.getResultado()).isEqualTo("RECHAZADO");
        assertThat(segundo.getIntento()).isEqualTo(2);
        assertThat(segundo.getCodigo()).isEqualTo(2800);
        assertThat(segundo.tieneCdr()).isTrue();   // la constancia del rechazo queda guardada
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
    void elReintentoPideALaBaseSoloLosQueNoAgotaronSusIntentosNiEstanPorConfirmar() {
        // El filtro va en la consulta (F-02 y 1033); que Oracle lo cumpla lo prueba ReintentoSunatIntegracionTest
        when(comprobantes.pendientesParaReintentar(eq(EstadoSunat.PENDIENTE), eq(20), eq(1033), any(), any())).thenReturn(List.of());
        when(notas.pendientesParaReintentar(eq(EstadoSunat.PENDIENTE), eq(20), eq(1033), any())).thenReturn(List.of());

        servicio.reintentarPendientes();
        verify(comprobantes).pendientesParaReintentar(eq(EstadoSunat.PENDIENTE), eq(20), eq(1033),
                eq(java.time.LocalDate.now().minusDays(3)), eq(org.springframework.data.domain.Limit.of(20)));
        verify(notas).pendientesParaReintentar(eq(EstadoSunat.PENDIENTE), eq(20), eq(1033), eq(org.springframework.data.domain.Limit.of(20)));
        verify(ose, after(300).never()).emitir(any());
    }

    /**
     * 1033: SUNAT ya tenía ese número por un envío anterior y no dice si fue aceptado. Antes se daba por
     * ACEPTADO y salía el correo "ha sido emitida" (informe de rechazos SUNAT, D-1): ahora queda sin validez.
     */
    @Test
    void yaRegistradoNoSeDaPorAceptadoNiSeEnviaPorCorreo() {
        Comprobante c = comprobante(14, EstadoSunat.PENDIENTE);
        when(ose.emitir(c)).thenReturn(new RespuestaOse(Resultado.POR_CONFIRMAR, 1033,
                "SUNAT ya tiene registrado este número por un envío anterior", null, null, new byte[]{1}, null));

        assertThat(servicio.enviarComprobante(14L)).isEqualTo(EstadoSunat.PENDIENTE);
        assertThat(c.getEstadoSunat().valido()).isFalse();
        assertThat(c.getEnvio().getCodigo()).isEqualTo(1033);
        assertThat(c.getEnvio().getCdr()).isNull();
        verifyNoInteractions(eventos);
    }

    /** Excepción (0200-1999): SUNAT no registró el comprobante, así que se reenvía con el mismo número. */
    @Test
    void reenviarUnaExcepcionLaVuelvePendienteConLosIntentosEnCero() throws InterruptedException {
        Comprobante c = comprobante(13, EstadoSunat.RECHAZADO);
        c.getEnvio().registrarIntento(305, "El RUC del receptor no existe", null, null);
        when(ose.emitir(c)).thenReturn(respuesta(Resultado.REINTENTAR, null, "sin respuesta"));

        servicio.reenviarComprobante(13L);
        // El reenvío va a la cola en otro hilo: se espera a que termine antes de mirar el resultado (B10)
        servicio.detener();

        verify(ose, times(1)).emitir(c);
        assertThat(c.getEstadoSunat()).isEqualTo(EstadoSunat.PENDIENTE);
        // Tenía 1 intento; el reenvío los pone en cero y el envío cuenta uno: si no se pusieran en cero, serían 2
        assertThat(c.getEnvio().getIntentos()).isEqualTo(1);
    }

    /** Rechazo con CDR (2000-3999) o 1032: el número quedó usado; no se reenvía, se emite un reemplazo (D-2). */
    @Test
    void unRechazoDefinitivoNoSeReenviaConElMismoNumero() {
        Comprobante c = comprobante(15, EstadoSunat.RECHAZADO);
        c.getEnvio().registrarIntento(2800, "Tipo de documento del receptor no permitido", null, new byte[]{2});
        assertThatThrownBy(() -> servicio.reenviarComprobante(15L))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("comprobante de reemplazo");

        Comprobante yaRechazado = comprobante(16, EstadoSunat.RECHAZADO);
        yaRechazado.getEnvio().registrarIntento(1032, "El comprobante ya esta informado y se encuentra con estado anulado o rechazado", null, null);
        assertThatThrownBy(() -> servicio.reenviarComprobante(16L)).isInstanceOf(ResponseStatusException.class);

        assertThat(c.getEstadoSunat()).isEqualTo(EstadoSunat.RECHAZADO);
        verify(ose, after(300).never()).emitir(any());
    }

    @Test
    void unComprobanteReemplazadoNoSeReenvia() {
        Comprobante c = comprobante(17, EstadoSunat.RECHAZADO);
        c.getEnvio().registrarIntento(305, "excepción", null, null);
        c.setReemplazado(true);
        assertThatThrownBy(() -> servicio.reenviarComprobante(17L))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("ya fue reemplazado");
    }

    // --- Usuario o clave SOL rechazados (informe D-9): se detienen todos los envíos ---

    @Test
    void conLasCredencialesRechazadasSeDetieneTodo() {
        Comprobante primero = comprobante(50, EstadoSunat.PENDIENTE);
        when(ose.emitir(primero)).thenReturn(new RespuestaOse(Resultado.CREDENCIALES, 102,
                "SUNAT rechazó el usuario o la clave SOL (código 102)", null, null, new byte[]{1}, null));

        assertThat(servicio.enviarComprobante(50L)).isEqualTo(EstadoSunat.PENDIENTE);
        assertThat(servicio.motivoEnvioDetenido()).contains("usuario o la clave SOL");
        verifyNoInteractions(eventos);

        // El siguiente ya no sale ni gasta un intento: SUNAT respondería lo mismo y podría bloquear la cuenta SOL
        Comprobante segundo = comprobante(51, EstadoSunat.PENDIENTE);
        assertThat(servicio.enviarComprobante(51L)).isEqualTo(EstadoSunat.PENDIENTE);
        verify(ose, never()).emitir(segundo);
        assertThat(segundo.getEnvio().getIntentos()).isZero();

        // Ni el reintento automático ni el botón Reenviar
        servicio.reintentarPendientes();
        verify(comprobantes, never()).pendientesParaReintentar(any(), anyInt(), anyInt(), any(), any());
        assertThatThrownBy(() -> servicio.reenviarComprobante(51L))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("detenidos");
    }

    // --- Tipo de comprobante y cliente (informe D-11) ---

    @Test
    void unaFacturaNoSeReenviaSiElClienteYaNoTieneRuc() {
        Comprobante c = comprobante(52, EstadoSunat.RECHAZADO);
        c.setTipo(Comprobante.TipoComprobante.FACTURA);
        c.getEnvio().registrarIntento(305, "El RUC del receptor no existe", null, null);
        com.bomerp.clientes.general.integration.Cliente cliente = new com.bomerp.clientes.general.integration.Cliente();
        cliente.setTipo(com.bomerp.clientes.general.integration.Cliente.TipoCliente.PERSONA_NATURAL);
        cliente.setDni("12345678");
        Venta v = new Venta();
        v.setId(52L);
        v.setCliente(cliente);
        c.setVenta(v);

        assertThat(c.necesitaOtroTipo()).isTrue();
        assertThatThrownBy(() -> servicio.reenviarComprobante(52L))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("ya no tiene RUC");
        assertThat(c.getEstadoSunat()).isEqualTo(EstadoSunat.RECHAZADO);

        // Si el cliente vuelve a ser empresa con RUC, la misma factura sí se reenvía
        cliente.setTipo(com.bomerp.clientes.general.integration.Cliente.TipoCliente.EMPRESA);
        cliente.setRuc("20100000001");
        assertThat(c.necesitaOtroTipo()).isFalse();
    }

    // --- Plazo de envío: 3 días calendario desde el día siguiente a la emisión (informe D-6) ---

    @Test
    void elPlazoVenceTresDiasDespuesDeLaEmision() {
        Comprobante c = comprobante(40, EstadoSunat.PENDIENTE);
        c.setFechaEmision(java.time.LocalDate.of(2026, 11, 7));
        assertThat(c.fechaLimiteEnvio()).isEqualTo(java.time.LocalDate.of(2026, 11, 10));
        assertThat(c.fueraDePlazo(java.time.LocalDate.of(2026, 11, 10))).isFalse();
        assertThat(c.fueraDePlazo(java.time.LocalDate.of(2026, 11, 11))).isTrue();

        // Ya tiene validez, ya lo rechazó SUNAT con CDR, está por confirmar (1033) o fue reemplazado: el plazo no aplica
        c.setEstadoSunat(EstadoSunat.ACEPTADO);
        assertThat(c.fueraDePlazo(java.time.LocalDate.of(2026, 12, 1))).isFalse();
        c.setEstadoSunat(EstadoSunat.RECHAZADO);
        c.getEnvio().registrarIntento(2800, "rechazo", null, new byte[]{2});
        assertThat(c.fueraDePlazo(java.time.LocalDate.of(2026, 12, 1))).isFalse();
        c.setEstadoSunat(EstadoSunat.PENDIENTE);
        c.getEnvio().registrarIntento(1033, "ya registrado", null, null);
        assertThat(c.fueraDePlazo(java.time.LocalDate.of(2026, 12, 1))).isFalse();
        // Una excepción (SUNAT no lo registró) sí vence
        c.setEstadoSunat(EstadoSunat.RECHAZADO);
        c.getEnvio().registrarIntento(305, "excepción", null, null);
        assertThat(c.fueraDePlazo(java.time.LocalDate.of(2026, 12, 1))).isTrue();
        c.setReemplazado(true);
        assertThat(c.fueraDePlazo(java.time.LocalDate.of(2026, 12, 1))).isFalse();
    }

    @Test
    void unPendienteFueraDePlazoNoSeEnviaYDiceQueHacer() {
        Comprobante c = comprobante(41, EstadoSunat.PENDIENTE);
        c.setFechaEmision(java.time.LocalDate.now().minusDays(4));

        assertThat(servicio.enviarComprobante(41L)).isEqualTo(EstadoSunat.PENDIENTE);
        verify(ose, never()).emitir(any());
        assertThat(c.getEnvio().getMensaje()).contains("venció el plazo").contains("comprobante de reemplazo");
        verifyNoInteractions(eventos);
    }

    @Test
    void unPendienteEmitidoHaceTresDiasTodaviaSeEnvia() {
        Comprobante c = comprobante(42, EstadoSunat.PENDIENTE);
        c.setFechaEmision(java.time.LocalDate.now().minusDays(3));
        when(ose.emitir(c)).thenReturn(respuesta(Resultado.ACEPTADO, 0, "aceptada"));
        assertThat(servicio.enviarComprobante(42L)).isEqualTo(EstadoSunat.ACEPTADO);
    }

    @Test
    void unaExcepcionFueraDePlazoNoSeReenvia() {
        Comprobante c = comprobante(43, EstadoSunat.RECHAZADO);
        c.setFechaEmision(java.time.LocalDate.now().minusDays(5));
        c.getEnvio().registrarIntento(305, "El RUC del receptor no existe", null, null);
        assertThatThrownBy(() -> servicio.reenviarComprobante(43L))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("venció el plazo");
        assertThat(c.getEstadoSunat()).isEqualTo(EstadoSunat.RECHAZADO);
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
