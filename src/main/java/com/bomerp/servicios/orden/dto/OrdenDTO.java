package com.bomerp.servicios.orden.dto;

import com.bomerp.postventa.equipo.dto.EquipoDTO;
import com.bomerp.servicios.orden.entity.OrdenTrabajo;
import com.bomerp.ventas.venta.entity.Venta;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Orden de trabajo: lo que se envía al programarla, cerrarla, cancelarla o facturarla, y lo que se devuelve. */
public final class OrdenDTO {

    private OrdenDTO() {
    }

    /** Programar (o editar mientras está PROGRAMADA). */
    public record Request(
            @NotNull(message = "Indique el tipo de trabajo") OrdenTrabajo.Tipo tipo,
            @NotNull(message = "Indique el cliente") Long clienteId,
            Long sedeId,
            Long ventaId,
            Long mantenimientoId,
            Long equipoId,
            @NotNull(message = "Indique la fecha") LocalDate fechaProgramada,
            @NotBlank(message = "Indique el técnico") @Size(max = 100) String tecnico,
            @Size(max = 200) String direccion,
            @NotBlank(message = "Describa el trabajo a realizar") @Size(max = 500) String descripcion
    ) {}

    public record MaterialRequest(
            @NotNull Long productoId,
            @NotNull @Min(value = 1, message = "La cantidad debe ser al menos 1") Integer cantidad
    ) {}

    /** Equipo que deja instalado una orden de INSTALACION. */
    public record EquipoInstaladoRequest(
            @NotNull Long productoId,
            @Size(max = 60) String numeroSerie,
            @Size(max = 100) String ubicacion,
            @Min(0) @Max(120) Integer mesesGarantia,
            Integer frecuenciaMeses
    ) {}

    public record Cierre(
            LocalDate fechaRealizada,                     // opcional: hoy
            @NotBlank(message = "Describa el trabajo realizado") @Size(max = 1000) String trabajoRealizado,
            @NotBlank(message = "Indique quién recibió el trabajo") @Size(max = 100) String recibidoPor,
            @Size(max = 500) String observaciones,
            List<@Valid MaterialRequest> materiales,
            List<@Valid EquipoInstaladoRequest> equipos
    ) {}

    public record Cancelacion(@NotNull @Size(min = 5, max = 300, message = "Indique el motivo (5 a 300 caracteres)") String motivo) {}

    public record ItemFactura(
            @NotNull Long productoId,
            @NotNull @Min(value = 1, message = "La cantidad debe ser al menos 1") Integer cantidad,
            @DecimalMin(value = "0") BigDecimal descuentoPorcentaje
    ) {}

    /** Facturar el servicio: líneas de mano de obra (productos del sector SERVICIO). */
    public record Facturacion(
            @NotEmpty(message = "Indique al menos un servicio a facturar") List<@Valid ItemFactura> items,
            @NotNull(message = "Indique si es al contado o a crédito") Venta.TipoPago tipoPago,
            Integer diasCredito,
            Venta.Moneda moneda,
            BigDecimal tipoCambio
    ) {}

    public record MaterialResponse(Long productoId, String productoNombre, int cantidad, BigDecimal costoUnitario, String moneda) {}

    public record Response(
            Long id,
            String tipo,
            String estado,
            Long clienteId,
            String clienteNombre,
            String clienteTelefono,
            Long sedeId,
            String sedeNombre,
            Long ventaId,
            Long mantenimientoId,
            Long equipoId,
            String equipoNombre,
            LocalDate fechaProgramada,
            String tecnico,
            String direccion,
            String descripcion,
            LocalDate fechaRealizada,
            String trabajoRealizado,
            String recibidoPor,
            String observaciones,
            List<MaterialResponse> materiales,
            List<EquipoDTO.Response> equiposInstalados,
            Long ventaFacturadaId,
            String registradoPor,
            String cerradoPor,
            String motivoCancelacion
    ) {
        public static Response desdeEntidad(OrdenTrabajo o, List<EquipoDTO.Response> equiposInstalados) {
            return new Response(
                    o.getId(),
                    o.getTipo().name(),
                    o.getEstado().name(),
                    o.getCliente().getId(),
                    o.getCliente().getNombreORazonSocial(),
                    o.getSede() != null && o.getSede().getTelefono() != null ? o.getSede().getTelefono() : o.getCliente().getTelefono(),
                    o.getSede() != null ? o.getSede().getId() : null,
                    o.getSede() != null ? o.getSede().getNombre() : null,
                    o.getVenta() != null ? o.getVenta().getId() : null,
                    o.getMantenimientoId(),
                    o.getEquipo() != null ? o.getEquipo().getId() : null,
                    o.getEquipo() != null ? o.getEquipo().getProducto().getNombre()
                            + (o.getEquipo().getNumeroSerie() != null ? " (serie " + o.getEquipo().getNumeroSerie() + ")" : "") : null,
                    o.getFechaProgramada(),
                    o.getTecnico(),
                    o.getDireccion(),
                    o.getDescripcion(),
                    o.getFechaRealizada(),
                    o.getTrabajoRealizado(),
                    o.getRecibidoPor(),
                    o.getObservaciones(),
                    o.getMateriales().stream()
                            .map(m -> new MaterialResponse(m.getProducto().getId(), m.getProducto().getNombre(),
                                    m.getCantidad(), m.getCostoUnitario(), m.getMoneda()))
                            .toList(),
                    equiposInstalados,
                    o.getVentaFacturadaId(),
                    o.getRegistradoPor(),
                    o.getCerradoPor(),
                    o.getMotivoCancelacion());
        }
    }
}
