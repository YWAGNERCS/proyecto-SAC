package com.bomerp.seguridad.descuento;

import com.bomerp.seguridad.acceso.DetalleHistorial;
import com.bomerp.seguridad.descuento.datos.TopesDescuento;
import com.bomerp.seguridad.descuento.datos.TopesDescuentoRepository;
import com.bomerp.seguridad.permisos.Permiso;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Descuento por línea (proformas y ventas) con un tope según el rol (o el permiso DESCUENTO_AMPLIADO) de quien lo registra.
 * Los topes los fija Lisandro en Configuración (tabla TOPES_DESCUENTO, solo el ADMIN); mientras no los cambie, valen
 * los de application.properties (app.descuentos.*). Sin usuario identificado se aplica el tope más bajo (el de asistente).
 */
@Service
public class TopeDescuentoService {

    private static final BigDecimal CIEN = new BigDecimal("100");

    private final BigDecimal topeAdminInicial;
    private final BigDecimal topeAsistenteInicial;
    private final TopesDescuentoRepository repository;

    public TopeDescuentoService(@Value("${app.descuentos.tope-admin:30}") BigDecimal topeAdmin,
                                @Value("${app.descuentos.tope-asistente:10}") BigDecimal topeAsistente,
                                TopesDescuentoRepository repository) {
        this.topeAdminInicial = topeAdmin;
        this.topeAsistenteInicial = topeAsistente;
        this.repository = repository;
    }

    /** Los dos topes vigentes, quién los cambió por última vez y cuándo (null si siguen los iniciales). */
    public record Topes(BigDecimal topeAdmin, BigDecimal topeAsistente, String actualizadoPor,
                        LocalDateTime fechaActualizacion) {}

    @Transactional(readOnly = true)
    public Topes topes() {
        return repository.findById(TopesDescuento.ID_UNICO)
                .map(t -> new Topes(t.getTopeAdmin(), t.getTopeAsistente(), t.getActualizadoPor(), t.getFechaActualizacion()))
                .orElse(new Topes(topeAdminInicial, topeAsistenteInicial, null, null));
    }

    /**
     * Cambia los topes (solo el ADMIN: SecurityConfig). Cada uno entre 0 % y 100 %, y el del asistente no mayor que
     * el del administrador. El historial guarda los valores de antes y de después.
     */
    @Transactional
    public Topes cambiar(BigDecimal topeAdmin, BigDecimal topeAsistente, String usuario) {
        BigDecimal admin = validarPorcentaje(topeAdmin, "del administrador");
        BigDecimal asistente = validarPorcentaje(topeAsistente, "del asistente");
        if (asistente.compareTo(admin) > 0) {
            throw new IllegalArgumentException("El tope del asistente no puede ser mayor que el del administrador.");
        }
        Topes antes = topes();
        TopesDescuento fila = repository.findById(TopesDescuento.ID_UNICO).orElseGet(TopesDescuento::new);
        fila.setTopeAdmin(admin);
        fila.setTopeAsistente(asistente);
        fila.setActualizadoPor(usuario);
        fila.setFechaActualizacion(LocalDateTime.now());
        repository.save(fila);
        DetalleHistorial.anotar("Administrador y descuento ampliado: " + texto(antes.topeAdmin()) + " → " + texto(admin)
                + " · asistente: " + texto(antes.topeAsistente()) + " → " + texto(asistente));
        return new Topes(admin, asistente, usuario, fila.getFechaActualizacion());
    }

    private static BigDecimal validarPorcentaje(BigDecimal valor, String de) {
        if (valor == null || valor.signum() < 0 || valor.compareTo(CIEN) > 0) {
            throw new IllegalArgumentException("El tope " + de + " debe estar entre 0 % y 100 %.");
        }
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    private static String texto(BigDecimal porcentaje) {
        return porcentaje.stripTrailingZeros().toPlainString() + " %";
    }

    /** Tope (%) del usuario de la petición actual. */
    public BigDecimal topeActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // El tope del administrador lo tiene el ADMIN y quien recibió el permiso DESCUENTO_AMPLIADO
        boolean ampliado = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())
                        || ("ROLE_" + Permiso.DESCUENTO_AMPLIADO.rol()).equals(a.getAuthority()));
        Topes topes = topes();
        return ampliado ? topes.topeAdmin() : topes.topeAsistente();
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
