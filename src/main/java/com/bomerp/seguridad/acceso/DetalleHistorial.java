package com.bomerp.seguridad.acceso;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Lo que un servicio quiere que el historial de cambios diga además de la acción ("Registró la venta #120"): por
 * ejemplo, qué precio se editó y por qué. Se guarda en la petición actual y AuditoriaFilter lo copia al detalle
 * del registro cuando la petición termina bien. Fuera de una petición web (pruebas, tareas programadas) no hace nada.
 */
public final class DetalleHistorial {

    public static final String ATRIBUTO = "sgejd.historial.detalle";

    private DetalleHistorial() {}

    /** Agrega una frase al detalle (separada por " · " de las anteriores). */
    public static void anotar(String texto) {
        if (texto == null || texto.isBlank()) return;
        RequestAttributes peticion = RequestContextHolder.getRequestAttributes();
        if (peticion == null) return;
        Object previo = peticion.getAttribute(ATRIBUTO, RequestAttributes.SCOPE_REQUEST);
        peticion.setAttribute(ATRIBUTO, previo == null ? texto : previo + " · " + texto, RequestAttributes.SCOPE_REQUEST);
    }
}
