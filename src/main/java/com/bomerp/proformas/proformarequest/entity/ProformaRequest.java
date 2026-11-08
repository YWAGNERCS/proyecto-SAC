package com.bomerp.proformas.proformarequest.entity;

import com.bomerp.proformas.proforma.entity.Proforma;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class ProformaRequest {

    public record CrearProformaDTO(
            @NotNull Long clienteId,
            @NotNull Proforma.Moneda moneda,
            Integer diasVigencia,
            // @Valid en cada ítem: sin él, el @Min(1) de la cantidad nunca se revisaba
            @NotEmpty(message = "La proforma debe tener al menos un producto") List<@Valid ItemDTO> items,
            java.math.BigDecimal tipoCambio          // opcional: soles por dólar (null = SUNAT del día)
    ) {}

    public record ItemDTO(
            @NotNull Long productoId,
            @NotNull @Min(value = 1, message = "La cantidad debe ser al menos 1") Integer cantidad,
            java.math.BigDecimal descuentoPorcentaje,   // opcional: % negociado (0 a tope del rol)
            // opcional: precio editado en la moneda de la proforma (null = catálogo); solo sube, con motivo y permiso
            @jakarta.validation.constraints.Positive(message = "El precio debe ser mayor que cero.") java.math.BigDecimal precioUnitario,
            String motivoPrecio
    ) {
        public ItemDTO(Long productoId, Integer cantidad, java.math.BigDecimal descuentoPorcentaje) {
            this(productoId, cantidad, descuentoPorcentaje, null, null);
        }
    }
}
