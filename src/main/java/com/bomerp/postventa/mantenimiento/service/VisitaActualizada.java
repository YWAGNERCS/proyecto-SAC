package com.bomerp.postventa.mantenimiento.service;

import java.time.LocalDate;
import com.bomerp.postventa.mantenimiento.entity.Mantenimiento.EstadoMantenimiento;

/** Cambio que las órdenes deben validar y aplicar dentro de la misma transacción. */
public record VisitaActualizada(Long id, LocalDate fecha, EstadoMantenimiento estado, String motivo) {}
