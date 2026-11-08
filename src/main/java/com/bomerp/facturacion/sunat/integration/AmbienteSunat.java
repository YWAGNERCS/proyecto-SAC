package com.bomerp.facturacion.sunat.integration;

import com.bomerp.facturacion.comprobante.entity.Comprobante.AmbienteEmision;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Ambiente de SUNAT al que envía SGE-JD: lo DECLARA la configuración (SUNAT_AMBIENTE) y se comprueba contra la dirección
 * OFICIAL de ese ambiente. Antes se deducía buscando "beta" en la URL, y cualquier otra dirección (un intermediario, un
 * proxy, un error al escribirla) quedaba como PRODUCCION: los comprobantes de pruebas habrían salido por correo a los
 * clientes como si tuvieran validez tributaria, y ese rótulo queda fijo en cada comprobante.
 *
 * Reglas (si no se cumplen, el backend no arranca y dice por qué):
 * - SUNAT_AMBIENTE es obligatorio en modo sunat: PRODUCCION o BETA (sin valor por defecto).
 * - SUNAT_URL tiene que ser exactamente la dirección oficial de ese ambiente: https, el servidor de SUNAT, puerto
 *   estándar, sin usuario, parámetros ni fragmentos. No se aceptan intermediarios ni otros servidores.
 */
public final class AmbienteSunat {

    /** billService de producción (SEE - Del Contribuyente): lo que se envía aquí tiene validez tributaria. */
    public static final String URL_PRODUCCION = "https://e-factura.sunat.gob.pe/ol-ti-itcpfegem/billService";

    /** billService de pruebas (beta): no tiene efecto tributario. */
    public static final String URL_BETA = "https://e-beta.sunat.gob.pe/ol-ti-itcpfegem-beta/billService";

    private AmbienteSunat() {}

    /**
     * @return el ambiente declarado, si la dirección es la oficial de ese ambiente
     * @throws IllegalStateException con el motivo (falta el ambiente, no es oficial, no corresponde al ambiente…)
     */
    public static AmbienteEmision validar(String ambiente, String url) {
        if (ambiente == null || ambiente.isBlank()) {
            throw new IllegalStateException("Falta SUNAT_AMBIENTE (app.sunat.ambiente): con FACTURACION_MODO=sunat hay que"
                    + " declarar PRODUCCION o BETA. No se deduce de la dirección.");
        }
        AmbienteEmision declarado = switch (ambiente.trim().toUpperCase(Locale.ROOT)) {
            case "PRODUCCION", "PRODUCCIÓN" -> AmbienteEmision.PRODUCCION;
            case "BETA" -> AmbienteEmision.BETA;
            default -> throw new IllegalStateException("SUNAT_AMBIENTE=" + ambiente.trim()
                    + " no es válido: tiene que ser PRODUCCION o BETA.");
        };
        String oficial = declarado == AmbienteEmision.PRODUCCION ? URL_PRODUCCION : URL_BETA;
        String otra = declarado == AmbienteEmision.PRODUCCION ? URL_BETA : URL_PRODUCCION;
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("Falta SUNAT_URL. Para " + declarado + " es " + oficial + ".");
        }
        if (esLaOficial(url, otra)) {
            throw new IllegalStateException("SUNAT_AMBIENTE=" + declarado + " pero SUNAT_URL es la dirección de "
                    + (declarado == AmbienteEmision.PRODUCCION ? "BETA" : "PRODUCCION") + " (" + otra
                    + "). Corrija una de las dos: tienen que corresponder.");
        }
        if (!esLaOficial(url, oficial)) {
            throw new IllegalStateException("SUNAT_URL=" + url.trim() + " no es la dirección oficial de SUNAT para "
                    + declarado + " (" + oficial + "). No se permiten intermediarios, otros servidores ni conexiones sin https.");
        }
        return declarado;
    }

    /**
     * Compara por partes, no como texto: https, el mismo servidor (sin importar mayúsculas), puerto estándar, sin
     * usuario@ delante del servidor, la misma ruta (con o sin "/" final) y sin parámetros ni fragmento.
     */
    static boolean esLaOficial(String url, String oficial) {
        try {
            URI u = new URI(url.trim());
            URI o = new URI(oficial);
            String ruta = u.getRawPath() == null ? "" : u.getRawPath().replaceAll("/+$", "");
            return "https".equalsIgnoreCase(u.getScheme())
                    && u.getRawUserInfo() == null
                    && u.getHost() != null && u.getHost().equalsIgnoreCase(o.getHost())
                    && (u.getPort() == -1 || u.getPort() == 443)
                    && ruta.equals(o.getRawPath())
                    && u.getRawQuery() == null && u.getRawFragment() == null;
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
