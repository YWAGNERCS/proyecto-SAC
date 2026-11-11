package com.bomerp.facturacion;

import com.bomerp.facturacion.sunat.integration.CircuitoSunat;
import com.bomerp.facturacion.sunat.integration.CircuitoSunat.Estado;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/** Circuit Breaker del envío a SUNAT (ADS S11): 50 % de fallos en las últimas 10, 5 minutos abierto, 1 prueba. */
class CircuitoSunatTest {

    /** Reloj que avanza solo cuando la prueba lo pide. */
    static class Reloj extends Clock {
        Instant ahora = Instant.parse("2026-11-10T15:00:00Z");
        void avanzar(Duration d) { ahora = ahora.plus(d); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return ahora; }
    }

    private final Reloj reloj = new Reloj();
    private final CircuitoSunat circuito = new CircuitoSunat(reloj);

    private void llamadas(boolean... fallos) {
        for (boolean f : fallos) {
            assertThat(circuito.permiteLlamar()).isTrue();
            circuito.registrar(f);
        }
    }

    @Test
    void unFalloAisladoEntreAciertosNoAbre() {
        llamadas(false, false, true, false, false, false, false, true, false, false);   // 2 de 10
        assertThat(circuito.estado()).isEqualTo(Estado.CERRADO);
        assertThat(circuito.aviso()).isNull();
    }

    @Test
    void conMenosDeCincoLlamadasNoDecideAunqueTodasFallen() {
        llamadas(true, true, true, true);
        assertThat(circuito.estado()).isEqualTo(Estado.CERRADO);
        llamadas(true);   // la quinta: 5 de 5
        assertThat(circuito.estado()).isEqualTo(Estado.ABIERTO);
    }

    @Test
    void unaCaidaRealAbreAunqueHayaUnAciertoDeSuerte() {
        llamadas(true, true, false, true, true);   // 4 de 5 = 80 %
        assertThat(circuito.estado()).isEqualTo(Estado.ABIERTO);
        assertThat(circuito.aviso()).contains("SUNAT no responde").contains("sin gastar intentos");
    }

    @Test
    void abiertoNoLlamaHastaQuePasenCincoMinutos() {
        llamadas(true, true, true, true, true);
        assertThat(circuito.permiteLlamar()).isFalse();
        reloj.avanzar(Duration.ofMinutes(4).plusSeconds(59));
        assertThat(circuito.permiteLlamar()).isFalse();
        reloj.avanzar(Duration.ofSeconds(1));
        assertThat(circuito.permiteLlamar()).isTrue();             // la única llamada de prueba
        assertThat(circuito.estado()).isEqualTo(Estado.SEMIABIERTO);
        assertThat(circuito.permiteLlamar()).isFalse();            // el resto del lote espera a la prueba
    }

    @Test
    void siLaPruebaRespondeSeCierra() {
        llamadas(true, true, true, true, true);
        reloj.avanzar(Duration.ofMinutes(5));
        assertThat(circuito.permiteLlamar()).isTrue();
        circuito.registrar(false);
        assertThat(circuito.estado()).isEqualTo(Estado.CERRADO);
        assertThat(circuito.permiteLlamar()).isTrue();
        assertThat(circuito.aviso()).isNull();
    }

    @Test
    void siLaPruebaFallaEsperaOtrosCincoMinutos() {
        llamadas(true, true, true, true, true);
        reloj.avanzar(Duration.ofMinutes(5));
        assertThat(circuito.permiteLlamar()).isTrue();
        circuito.registrar(true);
        assertThat(circuito.estado()).isEqualTo(Estado.ABIERTO);
        reloj.avanzar(Duration.ofMinutes(4));
        assertThat(circuito.permiteLlamar()).isFalse();
        reloj.avanzar(Duration.ofMinutes(1));
        assertThat(circuito.permiteLlamar()).isTrue();
    }

    @Test
    void siNoSeLlegoALlamarSeLiberaElTurnoDePrueba() {
        llamadas(true, true, true, true, true);
        reloj.avanzar(Duration.ofMinutes(5));
        assertThat(circuito.permiteLlamar()).isTrue();
        circuito.registrarSinLlamada();   // p. ej. faltaba la cuenta de detracciones
        assertThat(circuito.permiteLlamar()).isTrue();   // el siguiente comprobante puede ser la prueba
    }
}
