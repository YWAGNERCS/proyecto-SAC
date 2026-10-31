package com.bomerp.seguridad.descuento;

import com.bomerp.seguridad.permisos.Permiso;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Descuento por línea (proformas y ventas) con un tope según el rol (o el permiso DESCUENTO_AMPLIADO) de quien lo registra.
 * Los topes se configuran en application.properties (app.descuentos.*).
 * Sin usuario identificado se aplica el tope más bajo (el de asistente).
 */
@Service
public class TopeDescuentoService {

    private final BigDecimal topeAdmin;
    private final BigDecimal topeAsistente;

    public TopeDescuentoService(@Value("${app.descuentos.tope-admin:30}") BigDecimal topeAdmin,
                                @Value("${app.descuentos.tope-asistente:10}") BigDecimal topeAsistente) {
        this.topeAdmin = topeAdmin;
        this.topeAsistente = topeAsistente;
    }

    /** Tope (%) del usuario de la petición actual. */
    public BigDecimal topeActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // El tope del administrador lo tiene el ADMIN y quien recibió el permiso DESCUENTO_AMPLIADO
        boolean ampliado = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())
                        || ("ROLE_" + Permiso.DESCUENTO_AMPLIADO.rol()).equals(a.getAuthority()));
        return ampliado ? topeAdmin : topeAsistente;
    }

    /**
     * Valida el descuento de una línea: null = 0; entre 0 y el tope del rol (400 si no).
     *
     * @return el descuento normalizado a 2 decimales
     */
    public BigDecimal validar(BigDecimal descuento) {
        if (descuento == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal tope = topeActual();
        if (descuento.signum() < 0 || descuento.compareTo(tope) > 0) {
            throw new IllegalArgumentException("El descuento debe estar entre 0 % y "
                    + tope.stripTrailingZeros().toPlainString() + " % (tope de su usuario).");
        }
        return descuento.setScale(2, RoundingMode.HALF_UP);
    }
}
