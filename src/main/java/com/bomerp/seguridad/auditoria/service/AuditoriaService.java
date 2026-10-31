package com.bomerp.seguridad.auditoria.service;

import com.bomerp.seguridad.auditoria.entity.RegistroCambio;
import com.bomerp.seguridad.auditoria.repository.RegistroCambioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Historial de cambios (seguridad): traduce cada petición que modifica algo a una acción legible
 * ("Anuló la venta #113") y la guarda. Registrar nunca debe tumbar la operación del usuario: si la
 * escritura falla, se deja constancia en el log y se sigue.
 */
@Service
@Slf4j
public class AuditoriaService {

    /** Tope de filas que devuelve una consulta del historial. */
    public static final int MAXIMO = 500;

    private final RegistroCambioRepository repository;

    public AuditoriaService(RegistroCambioRepository repository) {
        this.repository = repository;
    }

    /** Ruta → módulo y descripción. {id} = número en la ruta; {nuevo} = id del registro creado. */
    private record Regla(String metodo, Pattern ruta, String modulo, String accion) {}

    private static Regla r(String metodo, String ruta, String modulo, String accion) {
        return new Regla(metodo, Pattern.compile("^/api/v1/admin" + ruta.replace("{n}", "(\\d+)").replace("{s}", "([^/]+)") + "/?$"),
                modulo, accion);
    }

    // El orden importa: la primera que coincide gana
    private static final List<Regla> REGLAS = List.of(
            r("PATCH", "/ventas/{n}/anular", "Ventas", "Anuló la venta #{id}"),
            r("POST", "/ventas/directa", "Ventas", "Registró la venta #{nuevo}"),
            r("POST", "/ventas/desde-proforma/{n}", "Ventas", "Convirtió la proforma #{id} en venta #{nuevo}"),
            r("POST", "/proformas", "Proformas", "Creó la proforma #{nuevo}"),
            r("POST", "/proformas/{n}/enviar", "Proformas", "Envió la proforma #{id}"),
            r("POST", "/proformas/{n}/aceptar", "Proformas", "Marcó como aceptada la proforma #{id}"),
            r("POST", "/proformas/{n}/rechazar", "Proformas", "Marcó como rechazada la proforma #{id}"),
            r("POST", "/proformas/marcar-vencidas", "Proformas", "Marcó las proformas vencidas"),
            r("POST", "/cobranza/cobros/{n}/anular", "Cobranza", "Anuló el cobro #{id}"),
            r("POST", "/cobranza/cobros", "Cobranza", "Registró el cobro #{nuevo}"),
            r("POST", "/cobranza/devoluciones/{n}/registrar", "Cobranza", "Registró la devolución #{id}"),
            r("POST", "/compras", "Compras", "Registró la compra #{nuevo}"),
            r("POST", "/cuentas-por-pagar/pagos/{n}/anular", "Compras", "Anuló el pago a proveedor #{id}"),
            r("POST", "/cuentas-por-pagar/pagos", "Compras", "Registró el pago a proveedor #{nuevo}"),
            r("POST", "/inventario/ajustes", "Inventario", "Ajustó el inventario (ajuste #{nuevo})"),
            r("POST", "/facturacion/emitir/{n}", "Facturación", "Emitió el comprobante de la venta #{id}"),
            r("POST", "/facturacion/{n}/enviar", "Facturación", "Envió el comprobante #{id}"),
            r("POST", "/productos", "Catálogo", "Creó el producto #{nuevo}"),
            r("PUT", "/productos/{n}", "Catálogo", "Modificó el producto #{id}"),
            r("DELETE", "/productos/{n}", "Catálogo", "Eliminó el producto #{id}"),
            r("POST", "/categorias", "Catálogo", "Creó la categoría #{nuevo}"),
            r("PUT", "/categorias/{n}", "Catálogo", "Modificó la categoría #{id}"),
            r("DELETE", "/categorias/{n}", "Catálogo", "Eliminó la categoría #{id}"),
            r("POST", "/clientes/{n}/sedes", "Clientes", "Agregó una sede al cliente #{id}"),
            r("PUT", "/clientes/{n}/sedes/{n}", "Clientes", "Modificó una sede del cliente #{id}"),
            r("DELETE", "/clientes/{n}/sedes/{n}", "Clientes", "Eliminó una sede del cliente #{id}"),
            r("POST", "/clientes", "Clientes", "Registró el cliente #{nuevo}"),
            r("PUT", "/clientes/{n}", "Clientes", "Modificó el cliente #{id}"),
            r("DELETE", "/clientes/{n}", "Clientes", "Eliminó el cliente #{id}"),
            r("POST", "/proveedores", "Proveedores", "Registró el proveedor #{nuevo}"),
            r("PUT", "/proveedores/{n}", "Proveedores", "Modificó el proveedor #{id}"),
            r("DELETE", "/proveedores/{n}", "Proveedores", "Eliminó el proveedor #{id}"),
            r("POST", "/ordenes", "Servicios", "Abrió la orden de trabajo #{nuevo}"),
            r("PUT", "/ordenes/{n}", "Servicios", "Modificó la orden de trabajo #{id}"),
            r("POST", "/ordenes/{n}/cerrar", "Servicios", "Cerró la orden de trabajo #{id}"),
            r("POST", "/ordenes/{n}/cancelar", "Servicios", "Canceló la orden de trabajo #{id}"),
            r("POST", "/ordenes/{n}/facturar", "Servicios", "Facturó la orden de trabajo #{id}"),
            r("POST", "/contratos", "Servicios", "Registró el contrato #{nuevo}"),
            r("POST", "/contratos/{n}/cancelar", "Servicios", "Canceló el contrato #{id}"),
            r("POST", "/equipos", "Servicios", "Registró el equipo instalado #{nuevo}"),
            r("PUT", "/equipos/{n}", "Servicios", "Modificó el equipo instalado #{id}"),
            r("POST", "/equipos/{n}/retirar", "Servicios", "Dio de baja el equipo #{id}"),
            r("PATCH", "/mantenimientos/{n}/realizado", "Servicios", "Marcó como realizado el mantenimiento #{id}"),
            r("POST", "/mantenimientos/{n}/marcar-realizado", "Servicios", "Marcó como realizado el mantenimiento #{id}"),
            r("PATCH", "/mantenimientos/{n}/reprogramar", "Servicios", "Reprogramó el mantenimiento #{id}"),
            r("PUT", "/permisos/{s}", "Permisos", "Cambió los permisos de {id}")
    );

    /** Módulo a partir del primer tramo de la ruta, para las rutas sin regla propia. */
    private static String moduloGenerico(String ruta) {
        String resto = ruta.replaceFirst("^/api/v1/admin/?", "");
        String tramo = resto.contains("/") ? resto.substring(0, resto.indexOf('/')) : resto;
        return switch (tramo) {
            case "tipo-cambio" -> "Tipo de cambio";
            case "descuentos" -> "Descuentos";
            case "inventario" -> "Inventario";
            case "" -> "Otro";
            default -> Character.toUpperCase(tramo.charAt(0)) + tramo.substring(1);
        };
    }

    private static final Map<String, String> INFINITIVOS = Map.ofEntries(
            Map.entry("Anuló", "anular"), Map.entry("Registró", "registrar"),
            Map.entry("Convirtió", "convertir"), Map.entry("Creó", "crear"),
            Map.entry("Envió", "enviar"), Map.entry("Marcó", "marcar"),
            Map.entry("Emitió", "emitir"), Map.entry("Modificó", "modificar"),
            Map.entry("Eliminó", "eliminar"), Map.entry("Agregó", "agregar"),
            Map.entry("Abrió", "abrir"), Map.entry("Cerró", "cerrar"),
            Map.entry("Canceló", "cancelar"), Map.entry("Facturó", "facturar"),
            Map.entry("Dio", "dar"), Map.entry("Reprogramó", "reprogramar"),
            Map.entry("Cambió", "cambiar"), Map.entry("Ajustó", "ajustar"));

    private static String intento(String accion) {
        int espacio = accion.indexOf(' ');
        String verbo = espacio < 0 ? accion : accion.substring(0, espacio);
        String infinitivo = INFINITIVOS.get(verbo);
        return infinitivo == null ? "Intento: " + accion : "Intentó " + infinitivo + accion.substring(espacio);
    }

    private static String verbo(String metodo) {
        return switch (metodo) {
            case "POST" -> "Registró";
            case "DELETE" -> "Eliminó";
            default -> "Modificó";
        };
    }

    /**
     * Guarda una acción. Se llama desde AuditoriaFilter cuando la petición ya terminó, en su propia
     * transacción: si la operación del usuario se revirtió, el intento igual queda registrado.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(String usuario, String roles, String metodo, String ruta, int estado,
                          String idCreado, String detalle, String ip) {
        try {
            RegistroCambio reg = new RegistroCambio();
            reg.setFechaHora(LocalDateTime.now());
            reg.setUsuario(recortar(usuario == null ? "anónimo" : usuario, 100));
            reg.setRoles(recortar(roles, 300));
            reg.setMetodo(metodo);
            reg.setRuta(recortar(ruta, 300));
            reg.setEstadoHttp(estado);
            reg.setResultado(estado == 401 || estado == 403 ? RegistroCambio.Resultado.DENEGADO
                    : estado >= 500 ? RegistroCambio.Resultado.ERROR
                    : estado >= 400 ? RegistroCambio.Resultado.RECHAZADO
                    : RegistroCambio.Resultado.EXITO);

            String modulo = moduloGenerico(ruta);
            String accion = verbo(metodo) + " en " + modulo.toLowerCase();
            for (Regla regla : REGLAS) {
                Matcher m = regla.ruta().matcher(ruta);
                if (regla.metodo().equals(metodo) && m.matches()) {
                    modulo = regla.modulo();
                    String id = m.groupCount() >= 1 ? m.group(1) : "";
                    String nuevo = idCreado != null && !idCreado.isBlank() ? idCreado : "?";
                    accion = regla.accion().replace("{id}", id).replace("{nuevo}", nuevo);
                    break;
                }
            }
            if (reg.getResultado() != RegistroCambio.Resultado.EXITO) {
                // No se hizo: "Eliminó la categoría #5" → "Intentó eliminar la categoría #5" (sin "#?" si no se creó nada)
                accion = intento(accion.replace(" #?", ""));
            }
            reg.setModulo(modulo);
            reg.setAccion(recortar(accion, 300));
            reg.setDetalle(recortar(detalle, 500));
            reg.setIp(recortar(ip, 64));
            repository.save(reg);
        } catch (RuntimeException e) {
            log.error("No se pudo registrar en el historial {} {} de {}: {}", metodo, ruta, usuario, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<RegistroCambio> buscar(String usuario, String modulo, RegistroCambio.Resultado resultado,
                                       LocalDate desde, LocalDate hasta) {
        return repository.buscar(vacioANulo(usuario), vacioANulo(modulo), resultado,
                desde == null ? null : desde.atStartOfDay(),
                hasta == null ? null : hasta.plusDays(1).atStartOfDay(),
                PageRequest.of(0, MAXIMO));
    }

    @Transactional(readOnly = true)
    public List<String> usuarios() {
        return repository.usuarios();
    }

    @Transactional(readOnly = true)
    public List<String> modulos() {
        return repository.modulos();
    }

    private static String vacioANulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String recortar(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
