package com.bomerp.cobranza.devolucion.dto;

import com.bomerp.cobranza.cobro.entity.Cobro;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Cómo y cuándo JD devolvió el dinero al cliente. */
public record DevolucionRequestDTO(
        LocalDate fecha,                                    // opcional: hoy
        @NotNull(message = "Indique cómo se devolvió el dinero") Cobro.MetodoPago metodoPago,
        @Size(max = 100) String referencia,
        @Size(max = 300) String notas
) {
}
