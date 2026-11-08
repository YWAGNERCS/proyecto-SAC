package com.bomerp.seguridad.acceso;

import com.bomerp.seguridad.permisos.Permiso;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Lo que puede hacer el usuario de la petición actual, según su rol (ADMIN) o un permiso delegado (PERM_*).
 * El ADMIN puede todo; un asistente, solo lo que Lisandro le dio en Configuración › Usuarios y permisos.
 */
@Component
public class AccesoUsuario {

    /** Registrar una venta con su fecha real, hasta 30 días atrás (auditoría M3; permiso VENTA_FECHA_PASADA). */
    public boolean puedeRegistrarVentasConFechaPasada() {
        return tieneRolOPermiso(Permiso.VENTA_FECHA_PASADA);
    }

    /** Subir el precio de una línea en proformas y ventas, con motivo (auditoría M1; permiso EDITAR_PRECIO). */
    public boolean puedeEditarPrecios() {
        return tieneRolOPermiso(Permiso.EDITAR_PRECIO);
    }

    /** Usuario de la petición actual (el mismo nombre que guarda el historial de cambios); null sin sesión. */
    public String usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() ? auth.getName() : null;
    }

    private static boolean tieneRolOPermiso(Permiso permiso) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || ("ROLE_" + permiso.rol()).equals(a.getAuthority()));
    }
}
