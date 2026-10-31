package com.bomerp.config.permisos;

import java.util.Arrays;
import java.util.Optional;

/**
 * Permisos que el administrador puede delegar a un asistente, uno por uno, sin hacerlo ADMIN.
 * Cada permiso es un rol de realm en Keycloak llamado "PERM_" + nombre (p. ej. PERM_ANULAR_VENTAS),
 * así viaja en el token como cualquier rol y SecurityConfig lo exige con hasAnyRole("ADMIN", rol()).
 * El ADMIN tiene todos implícitamente. Administrar permisos y ver el historial no se delega.
 */
public enum Permiso {

    ANULAR_VENTAS("Anular ventas", "Anula una venta: devuelve el stock, emite la nota de crédito y deja la devolución pendiente"),
    ANULAR_COBROS("Anular cobros", "Anula un cobro mal registrado (o un pago a proveedor): su monto vuelve al saldo"),
    DEVOLUCIONES("Registrar devoluciones", "Registra que se devolvió el dinero de una venta anulada"),
    CATALOGO("Editar el catálogo", "Crea, edita y elimina productos y categorías (costos, márgenes y precios)"),
    ELIMINAR("Eliminar clientes, proveedores y sedes", "Elimina registros que no tienen movimientos"),
    SERVICIOS("Administrar servicios", "Crea y cancela contratos, cancela órdenes de trabajo y retira equipos"),
    DESCUENTO_AMPLIADO("Descuento ampliado", "Usa el tope de descuento del administrador en proformas y ventas"),
    AJUSTAR_INVENTARIO("Ajustar inventario", "Registra conteos físicos, mermas, material dañado o perdido y stock inicial"),
    VER_RENTABILIDAD("Ver rentabilidad", "Ve costos, márgenes y ganancia por venta, cliente, producto y técnico");

    public static final String PREFIJO = "PERM_";

    private final String nombre;
    private final String descripcion;

    Permiso(String nombre, String descripcion) {
        this.nombre = nombre;
        this.descripcion = descripcion;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    /** Nombre del rol en Keycloak (y en el token): PERM_ANULAR_VENTAS. */
    public String rol() {
        return PREFIJO + name();
    }

    public static Optional<Permiso> deRol(String rol) {
        return Arrays.stream(values()).filter(p -> p.rol().equals(rol)).findFirst();
    }
}
