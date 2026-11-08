package com.bomerp.config;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Precio de una línea de proforma o de venta: el de catálogo o uno editado al registrarla (auditoría M1).
 *
 * Reglas: el precio editado solo puede SUBIR (para bajarlo está el descuento, que tiene su tope por rol), lo puede
 * editar el ADMIN o quien tenga el permiso "Editar precio en la venta" y pide un motivo de 5 a 200 caracteres. El
 * catálogo no cambia: solo esa línea. La línea guarda las dos cifras, quién lo editó y por qué.
 *
 * @param precioUnitario el que se cobra
 * @param precioCatalogo el de catálogo en la moneda del documento
 * @param editadoPor     usuario que lo editó (null = precio de catálogo)
 * @param motivo         por qué se editó (null = precio de catálogo)
 */
public record PrecioDeLinea(BigDecimal precioUnitario, BigDecimal precioCatalogo, String editadoPor, String motivo) {

    public static final int MOTIVO_MINIMO = 5;
    public static final int MOTIVO_MAXIMO = 200;

    public boolean editado() {
        return editadoPor != null;
    }

    /** Sin edición: se cobra el precio de catálogo. */
    public static PrecioDeLinea deCatalogo(BigDecimal catalogo) {
        BigDecimal c = catalogo.setScale(2, RoundingMode.HALF_UP);
        return new PrecioDeLinea(c, c, null, null);
    }

    /**
     * @param catalogo    precio de catálogo, ya en la moneda del documento
     * @param pedido      precio que se pidió (null o igual al de catálogo = sin edición)
     * @param motivo      por qué se editó
     * @param producto    nombre del producto, para los mensajes
     * @param simbolo     S/ o US$, para los mensajes
     * @param puedeEditar el usuario es ADMIN o tiene el permiso
     * @param usuario     quién lo edita
     * @throws IllegalArgumentException (400) precio menor al de catálogo o sin motivo
     * @throws SinPermisoException      (403) sin permiso para editar precios
     */
    public static PrecioDeLinea resolver(BigDecimal catalogo, BigDecimal pedido, String motivo, String producto,
                                         String simbolo, boolean puedeEditar, String usuario) {
        PrecioDeLinea deCatalogo = deCatalogo(catalogo);
        if (pedido == null) return deCatalogo;
        BigDecimal precio = pedido.setScale(2, RoundingMode.HALF_UP);
        if (precio.compareTo(deCatalogo.precioCatalogo()) == 0) return deCatalogo;
        if (precio.compareTo(deCatalogo.precioCatalogo()) < 0) {
            throw new IllegalArgumentException("El precio de " + producto + " no puede ser menor que el de catálogo ("
                    + simbolo + " " + deCatalogo.precioCatalogo().toPlainString() + "): para bajarlo use el descuento.");
        }
        if (!puedeEditar) {
            throw new SinPermisoException("No tiene permiso para editar precios. El administrador puede dárselo en "
                    + "Configuración › Usuarios y permisos (\"Editar precio en la venta\").");
        }
        String m = motivo == null ? "" : motivo.trim();
        if (m.length() < MOTIVO_MINIMO || m.length() > MOTIVO_MAXIMO) {
            throw new IllegalArgumentException("Indique por qué cambia el precio de " + producto + " (de "
                    + MOTIVO_MINIMO + " a " + MOTIVO_MAXIMO + " caracteres).");
        }
        return new PrecioDeLinea(precio, deCatalogo.precioCatalogo(), usuario == null ? "desconocido" : usuario, m);
    }

    /** "R32: S/ 525.00 → S/ 560.00 (motivo: subió el costo del proveedor)" para el historial de cambios. */
    public String paraHistorial(String producto, String simbolo) {
        return producto + ": " + simbolo + " " + precioCatalogo.setScale(2, RoundingMode.HALF_UP).toPlainString() + " → "
                + simbolo + " " + precioUnitario.setScale(2, RoundingMode.HALF_UP).toPlainString() + " (motivo: " + motivo + ")";
    }

    /** 403 con mensaje (GlobalExceptionHandler lo traduce como cualquier ResponseStatusException). */
    public static class SinPermisoException extends org.springframework.web.server.ResponseStatusException {
        public SinPermisoException(String mensaje) {
            super(org.springframework.http.HttpStatus.FORBIDDEN, mensaje);
        }
    }
}
