package com.bomerp.seguridad.descuento;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    /** Los dos topes (administrador y asistente) que se ven en Configuración. */
    @GetMapping("/topes")
    public TopeDescuentoService.Topes topes() {
        return topeDescuentoService.topes();
    }

    public record CambioTopesRequest(BigDecimal topeAdmin, BigDecimal topeAsistente) {}

    /** Solo el ADMIN (SecurityConfig): Lisandro no lo delega. */
    @PutMapping("/topes")
    public TopeDescuentoService.Topes cambiar(@RequestBody CambioTopesRequest pedido, @AuthenticationPrincipal Jwt jwt) {
        String usuario = jwt != null ? jwt.getClaimAsString("preferred_username") : null;
        return topeDescuentoService.cambiar(pedido.topeAdmin(), pedido.topeAsistente(), usuario);
    }
}
