package com.bomerp.proformas.proformarequest.entity;

import com.bomerp.proformas.proforma.entity.Proforma;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class ProformaRequest {

    public record CrearProformaDTO(
            @NotNull Long clienteId,
            @NotNull Proforma.Moneda moneda,
            Integer diasVigencia,
            @NotNull List<ItemDTO> items
    ) {}

    public record ItemDTO(
            @NotNull Long productoId,
            @NotNull @Min(1) Integer cantidad
    ) {}
}
