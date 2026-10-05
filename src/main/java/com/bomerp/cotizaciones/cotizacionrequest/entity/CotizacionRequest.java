package com.bomerp.cotizaciones.cotizacionrequest.entity;

import com.bomerp.cotizaciones.cotizacion.entity.Cotizacion;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class CotizacionRequest {

    public record CrearCotizacionDTO(
            @NotNull Long clienteId,
            @NotNull Cotizacion.Moneda moneda,
            Integer diasVigencia,
            @NotNull List<ItemDTO> items
    ) {}

    public record ItemDTO(
            @NotNull Long productoId,
            @NotNull @Min(1) Integer cantidad
    ) {}
}
