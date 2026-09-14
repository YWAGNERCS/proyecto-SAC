package com.jdrefrigeracion.cotizaciones.models;
import com.jdrefrigeracion.cotizaciones.models.Cotizacion;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * DTOs de entrada (lo que llega desde el formulario del panel admin).
 * Separar esto de la entidad JPA evita exponer campos internos y
 * permite validar la forma exacta en que se recibe la data del cliente.
 */
public class CotizacionRequest {

    public record CrearCotizacionDTO(
            @NotNull Long clienteId,
            @NotNull Cotizacion.Moneda moneda,
            Integer diasVigencia, // ej. 15 o 30 días, según lo que definan con Lisandro
            @NotNull List<ItemDTO> items
    ) {}

    public record ItemDTO(
            @NotNull Long productoId,
            @NotNull @Min(1) Integer cantidad
    ) {}
}
