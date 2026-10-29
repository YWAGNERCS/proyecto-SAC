package com.bomerp.postventa.contrato.dto;

import com.bomerp.postventa.contrato.entity.ContratoMantenimiento;
import com.bomerp.ventas.venta.entity.Venta;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Contrato de mantenimiento: lo que se envía al crearlo y lo que se devuelve. */
public final class ContratoDTO {

    private ContratoDTO() {
    }

    public record Request(
            @NotNull(message = "Indique el cliente") Long clienteId,
            Long sedeId,
            @NotNull(message = "Indique la fecha de inicio") LocalDate fechaInicio,
            @NotNull(message = "Indique la fecha de fin") LocalDate fechaFin,
            @NotNull(message = "Indique cada cuántos meses es la visita") Integer frecuenciaMeses,
            @NotNull(message = "Indique el monto por visita")
            @DecimalMin(value = "0", inclusive = false, message = "El monto por visita debe ser mayor que 0") BigDecimal montoVisita,
            @NotNull(message = "Indique la moneda") Venta.Moneda moneda,
            @NotBlank(message = "Describa qué cubre el contrato") @Size(max = 300) String descripcion
    ) {}

    public record Cancelacion(@NotNull @Size(min = 5, max = 300, message = "Indique el motivo (5 a 300 caracteres)") String motivo) {}

    public record Response(
            Long id,
            Long clienteId,
            String clienteNombre,
            Long sedeId,
            String sedeNombre,
            LocalDate fechaInicio,
            LocalDate fechaFin,
            int frecuenciaMeses,
            BigDecimal montoVisita,
            String moneda,
            String descripcion,
            String estado,
            String registradoPor,
            String motivoCancelacion,
            long visitasPendientes,
            long visitasRealizadas
    ) {
        public static Response desdeEntidad(ContratoMantenimiento k, long pendientes, long realizadas) {
            return new Response(
                    k.getId(),
                    k.getCliente().getId(),
                    k.getCliente().getNombreORazonSocial(),
                    k.getSede() != null ? k.getSede().getId() : null,
                    k.getSede() != null ? k.getSede().getNombre() : null,
                    k.getFechaInicio(),
                    k.getFechaFin(),
                    k.getFrecuenciaMeses(),
                    k.getMontoVisita(),
                    k.getMoneda().name(),
                    k.getDescripcion(),
                    k.getEstado().name(),
                    k.getRegistradoPor(),
                    k.getMotivoCancelacion(),
                    pendientes,
                    realizadas);
        }
    }
}
