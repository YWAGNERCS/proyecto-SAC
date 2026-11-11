package com.bomerp.facturacion.sunat.integration;

import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Circuit Breaker del envío a SUNAT (ADS S11, sección 2).
 *
 * Problema que resuelve: si SUNAT se cae, cada comprobante pendiente ocupaba la cola hasta 30 s y gastaba uno de sus
 * 20 intentos; una caída de 2 horas los agotaba y quedaban fuera del reintento automático, sin culpa propia.
 *
 * Estados:
 * <ul>
 *   <li><b>CERRADO</b>: se llama a SUNAT. Se recuerdan las últimas {@code ventana} llamadas; si al menos
 *       {@code minimoLlamadas} fallaron en un {@code umbral} o más (por ejemplo 5 de 10), se abre.</li>
 *   <li><b>ABIERTO</b>: no se llama a SUNAT durante {@code espera}. El envío queda PENDIENTE sin gastar intento.</li>
 *   <li><b>SEMIABIERTO</b>: pasada la espera, se deja pasar UNA llamada de prueba. Si SUNAT responde, se cierra; si
 *       no, otra espera completa. Así no se golpea a SUNAT con todo el lote apenas vuelve.</li>
 * </ul>
 * Fallo = SUNAT no respondió o respondió que no puede procesar ahora (red, tiempo agotado, HTTP 5xx, 0100-0199).
 * Un rechazo con CDR, una observación o un 1033 NO son fallos: SUNAT respondió y el problema es del comprobante.
 */
@Slf4j
public class CircuitoSunat {

    public enum Estado { CERRADO, ABIERTO, SEMIABIERTO }

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final int ventana;
    private final int minimoLlamadas;
    private final double umbral;
    private final Duration espera;
    private final Clock reloj;

    private final Deque<Boolean> ultimas = new ArrayDeque<>();   // true = fallo
    private Estado estado = Estado.CERRADO;
    private Instant abiertoHasta;
    private Instant abiertoDesde;
    private boolean pruebaEnCurso;

    /** Valores de ADS S11: 50 % de fallos en las últimas 10 llamadas (con al menos 5), 5 minutos abierto. */
    public CircuitoSunat(Clock reloj) {
        this(10, 5, 0.5, Duration.ofMinutes(5), reloj);
    }

    public CircuitoSunat(int ventana, int minimoLlamadas, double umbral, Duration espera, Clock reloj) {
        this.ventana = ventana;
        this.minimoLlamadas = minimoLlamadas;
        this.umbral = umbral;
        this.espera = espera;
        this.reloj = reloj;
    }

    /**
     * ¿Se puede llamar a SUNAT ahora? Con el circuito abierto, no. Pasada la espera, deja pasar una sola llamada
     * de prueba (las demás siguen esperando hasta que esa responda).
     */
    public synchronized boolean permiteLlamar() {
        if (estado == Estado.CERRADO) {
            return true;
        }
        if (estado == Estado.ABIERTO && !reloj.instant().isBefore(abiertoHasta)) {
            estado = Estado.SEMIABIERTO;
            pruebaEnCurso = false;
            log.info("Circuito SUNAT semiabierto: se prueba con un envío");
        }
        if (estado == Estado.SEMIABIERTO && !pruebaEnCurso) {
            pruebaEnCurso = true;
            return true;
        }
        return false;
    }

    /** Resultado de una llamada que sí se hizo. */
    public synchronized void registrar(boolean fallo) {
        if (estado == Estado.SEMIABIERTO) {
            pruebaEnCurso = false;
            if (fallo) {
                abrir("la llamada de prueba tampoco respondió");
            } else {
                estado = Estado.CERRADO;
                ultimas.clear();
                log.info("Circuito SUNAT cerrado: SUNAT volvió a responder");
            }
            return;
        }
        ultimas.addLast(fallo);
        if (ultimas.size() > ventana) {
            ultimas.removeFirst();
        }
        long fallos = ultimas.stream().filter(f -> f).count();
        if (ultimas.size() >= minimoLlamadas && fallos >= Math.ceil(umbral * ultimas.size())) {
            abrir(fallos + " de las últimas " + ultimas.size() + " llamadas fallaron");
        }
    }

    /**
     * Se reservó el turno pero no se llegó a llamar a SUNAT (falta un dato de JD, el certificado falló al firmar).
     * No cuenta como fallo ni como acierto; en semiabierto, libera el turno para la siguiente llamada de prueba.
     */
    public synchronized void registrarSinLlamada() {
        if (estado == Estado.SEMIABIERTO) {
            pruebaEnCurso = false;
        }
    }

    private void abrir(String porque) {
        Instant ahora = reloj.instant();
        if (estado != Estado.SEMIABIERTO || abiertoDesde == null) {
            abiertoDesde = ahora;
        }
        estado = Estado.ABIERTO;
        abiertoHasta = ahora.plus(espera);
        ultimas.clear();
        log.warn("Circuito SUNAT abierto hasta {}: {}", abiertoHasta, porque);
    }

    public synchronized Estado estado() {
        return estado;
    }

    /** Aviso para la campana mientras SUNAT no responde, o null si el circuito está cerrado. */
    public synchronized String aviso() {
        if (estado == Estado.CERRADO || abiertoDesde == null) {
            return null;
        }
        LocalTime desde = LocalTime.ofInstant(abiertoDesde, ZoneId.systemDefault());
        return "SUNAT no responde desde las " + desde.format(HORA) + ". Los comprobantes quedan pendientes sin gastar "
                + "intentos y se enviarán solos cuando SUNAT vuelva.";
    }
}
