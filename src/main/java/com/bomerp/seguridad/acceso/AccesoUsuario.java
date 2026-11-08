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

    private static boolean tieneRolOPermiso(Permiso permiso) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || ("ROLE_" + permiso.rol()).equals(a.getAuthority()));
    }
}
