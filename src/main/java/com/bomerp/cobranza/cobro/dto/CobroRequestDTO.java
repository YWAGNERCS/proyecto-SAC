package com.bomerp.cobranza.cobro.dto;

import com.bomerp.cobranza.cobro.entity.Cobro;
import com.bomerp.ventas.venta.entity.Venta;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Pago de un cliente repartido entre una o varias de sus ventas a crédito. */
public record CobroRequestDTO(
        @NotNull Long clienteId,
        LocalDate fecha,                                    // opcional: hoy
        @NotNull Venta.Moneda moneda,
        @NotNull(message = "Indique cómo pagó el cliente") Cobro.MetodoPago metodoPago,
        @Size(max = 100) String referencia,
        @Size(max = 300) String notas,
        @NotEmpty(message = "Indique al menos una venta a la que abonar") List<@Valid AbonoDTO> abonos,
        @DecimalMin(value = "0", inclusive = false) BigDecimal tipoCambioRetencion
) {
    public CobroRequestDTO(Long clienteId, LocalDate fecha, Venta.Moneda moneda, Cobro.MetodoPago metodoPago,
            String referencia, String notas, List<AbonoDTO> abonos) {
        this(clienteId, fecha, moneda, metodoPago, referencia, notas, abonos, null);
    }
    public record AbonoDTO(
            @NotNull Long ventaId,
            @NotNull @DecimalMin(value = "0", inclusive = false, message = "El monto debe ser mayor que 0")
            BigDecimal monto,
            @DecimalMin(value = "0", inclusive = false) BigDecimal baseRetencion
    ) {
        public AbonoDTO(Long ventaId, BigDecimal monto) { this(ventaId, monto, null); }
    }
}
