package com.bomerp.ventas.venta.event;

import java.time.LocalDate;

/**
 * Rutina de mantenimiento que el vendedor elige al vender un equipo de climatización o refrigeración
 * (botón "Implementar rutinas de mantenimiento"). Viaja con {@link VentaRegistradaEvent} y Postventa
 * programa las visitas. Sin rutina, la venta no programa mantenimientos.
 *
 * @param frecuenciaMeses cada cuántos meses (1 a 12)
 * @param visitas         cuántas visitas (1 a 24)
 * @param primeraVisita   fecha de la primera visita (null = la fecha de la venta más la frecuencia)
 */
public record RutinaMantenimiento(Integer frecuenciaMeses, Integer visitas, LocalDate primeraVisita) {

    public static final int MAX_FRECUENCIA_MESES = 12;
    public static final int MAX_VISITAS = 24;

    /** Fecha de cada visita: la primera y luego cada {@code frecuenciaMeses}. */
    public LocalDate fechaVisita(int indice) {
        return primeraVisita.plusMonths((long) frecuenciaMeses * indice);
    }
}
