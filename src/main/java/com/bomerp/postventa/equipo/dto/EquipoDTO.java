package com.bomerp.postventa.equipo.dto;

import com.bomerp.postventa.equipo.entity.EquipoInstalado;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Equipo instalado: lo que se envía al registrarlo o editarlo y lo que se devuelve. */
public final class EquipoDTO {

    private EquipoDTO() {
    }

    /** Registro a mano (equipos instalados antes del sistema) o desde una orden de instalación. */
    public record Registro(
            @NotNull(message = "Indique el cliente") Long clienteId,
            Long sedeId,
            @NotNull(message = "Indique el equipo") Long productoId,
            @Size(max = 60) String numeroSerie,
            @Size(max = 100) String ubicacion,
            @NotNull(message = "Indique la fecha de instalación") LocalDate fechaInstalacion,
            @Min(value = 0, message = "La garantía no puede ser negativa") @Max(value = 120, message = "La garantía es de 120 meses como máximo")
            Integer mesesGarantia,
            Integer frecuenciaMeses,
            Long ventaId,
            @Size(max = 300) String notas
    ) {}

    /** Lo que se puede cambiar después: no el cliente ni el modelo (para eso se retira y se registra otro). */
    public record Edicion(
            Long sedeId,
            @Size(max = 60) String numeroSerie,
            @Size(max = 100) String ubicacion,
            @NotNull(message = "Indique los meses de garantía")
            @Min(value = 0, message = "La garantía no puede ser negativa") @Max(value = 120, message = "La garantía es de 120 meses como máximo")
            Integer mesesGarantia,
            Integer frecuenciaMeses,
            @Size(max = 300) String notas
    ) {}

    public record Retiro(@NotNull @Size(min = 5, max = 200, message = "Indique el motivo (5 a 200 caracteres)") String motivo) {}

    public record Response(
            Long id,
            Long clienteId,
            String clienteNombre,
            Long sedeId,
            String sedeNombre,
            Long productoId,
            String productoNombre,
            String sector,
            String numeroSerie,
            String ubicacion,
            LocalDate fechaInstalacion,
            int mesesGarantia,
            LocalDate garantiaHasta,
            boolean enGarantia,
            Integer frecuenciaMeses,
            LocalDate proximoMantenimiento,
            String estado,
            Long ventaId,
            Long ordenTrabajoId,
            String notas
    ) {
        public static Response desdeEntidad(EquipoInstalado e, LocalDate proximoMantenimiento) {
            return new Response(
                    e.getId(),
                    e.getCliente().getId(),
                    e.getCliente().getNombreORazonSocial(),
                    e.getSede() != null ? e.getSede().getId() : null,
                    e.getSede() != null ? e.getSede().getNombre() : null,
                    e.getProducto().getId(),
                    e.getProducto().getNombre(),
                    e.getProducto().getSector().name(),
                    e.getNumeroSerie(),
                    e.getUbicacion(),
                    e.getFechaInstalacion(),
                    e.getMesesGarantia(),
                    e.garantiaHasta(),
                    e.enGarantia(),
                    e.getFrecuenciaMeses(),
                    proximoMantenimiento,
                    e.getEstado().name(),
                    e.getVenta() != null ? e.getVenta().getId() : null,
                    e.getOrdenTrabajoId(),
                    e.getNotas());
        }
    }
}
