package com.bomerp.config.descuento;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/descuentos")
public class DescuentoController {

    private final TopeDescuentoService topeDescuentoService;

    public DescuentoController(TopeDescuentoService topeDescuentoService) {
        this.topeDescuentoService = topeDescuentoService;
    }

    /** Descuento máximo por línea (%) que puede dar el usuario actual. */
    @GetMapping("/tope")
    public Map<String, BigDecimal> tope() {
        return Map.of("topePorcentaje", topeDescuentoService.topeActual());
    }
}
