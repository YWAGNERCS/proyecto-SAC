package com.bomerp;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S14 · Prueba de punta a punta real: la petición HTTP pasa por la cadena de seguridad (JWT y roles), el
 * filtro de auditoría, los controladores, los servicios, los eventos entre módulos y Oracle, sin ningún
 * mock en medio. Recorre el flujo completo de JD Refrigeración:
 *
 * <pre>
 * producto → proforma → aceptar → venta (con rutina de mantenimiento) → comprobante → cobro parcial
 *   → cobro total → mantenimientos programados → anulación (stock, visitas, devolución) → auditoría
 * </pre>
 *
 * Datos: crea sus propios productos (no depende del stock de otra corrida) y usa un cliente existente que no
 * es agente de retención. Toda la prueba corre en una transacción que se deshace al final: no deja ventas
 * en la base ni envía nada a SUNAT (el envío ocurre solo después de confirmar la transacción). El historial
 * de cambios se guarda en su propia transacción (así lo exige RF-29), por eso sus filas se verifican y
 * luego se borran en {@link #limpiarAuditoria()}.
 *
 * Requiere Oracle encendido (Keycloak no: el token se simula con {@code jwt()}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FlujoCompletoEndToEndTest {

    private static final String API = "/api/v1/admin";
    /** Usuario de la prueba: así sus filas del historial se distinguen de las de personas reales. */
    private static final String USUARIO = "prueba-e2e";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transacciones;
    @Autowired private JwtAuthenticationConverter conversorDeRoles;

    private Long clienteId;
    private Long categoriaId;

    @BeforeEach
    void preparar() {
        clienteId = jdbc.queryForObject("SELECT MIN(id) FROM clientes WHERE agente_retencion = 0", Long.class);
        categoriaId = jdbc.queryForObject("SELECT MIN(id) FROM categorias", Long.class);
    }

    /** El historial se guarda aparte (REQUIRES_NEW); se borra también aparte para no dejar rastro. */
    @AfterEach
    void limpiarAuditoria() {
        TransactionTemplate nueva = new TransactionTemplate(transacciones);
        nueva.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        nueva.executeWithoutResult(t -> jdbc.update("DELETE FROM historial_cambios WHERE usuario = ?", USUARIO));
    }

    // ── Token simulado (ya validado): mismos claims que emite Keycloak ─────────────────────────

    /** Los roles se leen con el mismo conversor de la aplicación (SecurityConfig), no con uno de prueba. */
    private JwtRequestPostProcessor como(String rol) {
        return jwt().jwt(j -> j.subject(USUARIO).claim("preferred_username", USUARIO)
                        .claim("realm_access", Map.of("roles", List.of(rol))))
                .authorities(j -> conversorDeRoles.convert(j).getAuthorities());
    }

    private ResultActions enviar(String metodo, String url, String json, String rol) throws Exception {
        var peticion = switch (metodo) {
            case "POST" -> post(url);
            case "PATCH" -> patch(url);
            default -> get(url);
        };
        if (json != null) peticion.contentType(MediaType.APPLICATION_JSON).content(json);
        if (rol != null) peticion.with(como(rol));
        return mvc.perform(peticion);
    }

    private String ok(String metodo, String url, String json) throws Exception {
        return enviar(metodo, url, json, "ADMIN").andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
    }

    private Long crearProducto(String nombre, String sector, Integer stock) throws Exception {
        String json = ok("POST", API + "/productos", """
                {"nombre": "%s", "categoriaId": %d, "sector": "%s", "costoBase": 1000.00,
                 "margenPorcentaje": 20.00, "moneda": "SOLES", "stockActual": %s, "stockMinimo": 0,
                 "visibleEnCatalogo": false}""".formatted(nombre, categoriaId, sector, stock));
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private int stock(Long productoId) throws Exception {
        return JsonPath.read(ok("GET", API + "/productos/" + productoId, null), "$.stockActual");
    }

    private List<Map<String, Object>> visitasDeLaVenta(long ventaId) throws Exception {
        return JsonPath.read(ok("GET", API + "/mantenimientos?vista=TODOS&clienteId=" + clienteId, null),
                "$[?(@.ventaId == " + ventaId + ")]");
    }

    @Test
    void proformaVentaCobroMantenimientoYAnulacion() throws Exception {
        // 1. Catálogo: un equipo (sin stock, lleva mantenimiento) y un material con 10 unidades
        Long equipo = crearProducto("E2E split 12 000 BTU", "CLIMATIZACION", null);
        Long material = crearProducto("E2E tubería de cobre 3/8", "MATERIAL_SERVICIO", 10);

        // 2. Proforma con los dos, y el cliente la acepta
        String proforma = ok("POST", API + "/proformas", """
                {"clienteId": %d, "moneda": "SOLES", "diasVigencia": 15, "tipoCambio": 3.50,
                 "items": [{"productoId": %d, "cantidad": 1}, {"productoId": %d, "cantidad": 3}]}"""
                .formatted(clienteId, equipo, material));
        long proformaId = ((Number) JsonPath.read(proforma, "$.id")).longValue();
        assertThat((Boolean) JsonPath.read(proforma, "$.items[0].requiereMantenimiento")).isTrue();
        ok("POST", API + "/proformas/" + proformaId + "/aceptar", null);

        // 3. Venta a crédito desde la proforma, con la rutina del botón "Implementar rutinas de mantenimiento"
        String venta = ok("POST", API + "/ventas/desde-proforma/" + proformaId + "?tipoPago=CREDITO&diasCredito=30",
                """
                {"rutinaMantenimiento": {"frecuenciaMeses": 3, "visitas": 4, "primeraVisita": null}}""");
        long ventaId = ((Number) JsonPath.read(venta, "$.id")).longValue();
        BigDecimal total = new BigDecimal(JsonPath.read(venta, "$.total").toString());
        assertThat((String) JsonPath.read(venta, "$.estado")).isEqualTo("REGISTRADA");
        assertThat((String) JsonPath.read(venta, "$.estadoPago")).isEqualTo("PENDIENTE");
        assertThat(stock(material)).as("el material se descuenta en la misma transacción").isEqualTo(7);
        List<String> estadoProforma = JsonPath.read(ok("GET", API + "/proformas/pagina?clienteId=" + clienteId + "&tamano=100", null),
                "$.contenido[?(@.id == " + proformaId + ")].estado");
        assertThat(estadoProforma).containsExactly("CONVERTIDA");

        // 4. Facturación escuchó el evento: comprobante emitido, pendiente de envío a SUNAT
        String estados = ok("GET", API + "/facturacion/estados?ventaIds=" + ventaId, null);
        assertThat((String) JsonPath.read(estados, "$[0].comprobante.estado")).isEqualTo("PENDIENTE");

        // 5. Postventa escuchó el evento: 4 visitas, cada 3 meses desde la venta
        LocalDate hoy = LocalDate.now();
        assertThat(visitasDeLaVenta(ventaId)).extracting(m -> m.get("fechaProgramada").toString())
                .containsExactlyInAnyOrder(hoy.plusMonths(3).toString(), hoy.plusMonths(6).toString(),
                        hoy.plusMonths(9).toString(), hoy.plusMonths(12).toString());

        // 6. Listado paginado y agregado del servidor. Verificación cruzada (S14, Tabla 6 paso 4): el total que
        //    calcula el servidor sobre TODO el filtro es igual a sumar a mano las filas de todas las páginas.
        String pagina = ok("GET", API + "/ventas/pagina?clienteId=" + clienteId + "&tamano=5", null);
        int paginas = JsonPath.read(pagina, "$.ventas.totalPaginas");
        List<Number> montoSoles = JsonPath.read(pagina, "$.resumen[?(@.moneda == 'SOLES')].montoTotal");
        BigDecimal sumaManual = BigDecimal.ZERO;
        boolean ventaListada = false;
        for (int p = 0; p < paginas; p++) {
            String filas = ok("GET", API + "/ventas/pagina?clienteId=" + clienteId + "&tamano=5&pagina=" + p, null);
            List<Number> totales = JsonPath.read(filas, "$.ventas.contenido[?(@.estado == 'REGISTRADA' && @.moneda == 'SOLES')].total");
            for (Number t : totales) sumaManual = sumaManual.add(new BigDecimal(t.toString()));
            ventaListada |= !((List<?>) JsonPath.read(filas, "$.ventas.contenido[?(@.id == " + ventaId + ")]")).isEmpty();
        }
        assertThat(paginas).as("el filtro ocupa más de una página").isGreaterThanOrEqualTo(1);
        assertThat(ventaListada).isTrue();
        assertThat(new BigDecimal(montoSoles.get(0).toString())).isEqualByComparingTo(sumaManual);

        // 7. Cobranza: un abono parcial deja la venta pendiente; el resto la deja PAGADO
        BigDecimal parcial = total.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        String cobro = """
                {"clienteId": %d, "moneda": "SOLES", "metodoPago": "EFECTIVO",
                 "abonos": [{"ventaId": %d, "monto": %s}]}""";
        ok("POST", API + "/cobranza/cobros", cobro.formatted(clienteId, ventaId, parcial));
        assertThat((List<?>) JsonPath.read(ok("GET", API + "/cobranza/por-cobrar?clienteId=" + clienteId, null),
                "$[?(@.ventaId == " + ventaId + ")]")).hasSize(1);
        ok("POST", API + "/cobranza/cobros", cobro.formatted(clienteId, ventaId, total.subtract(parcial)));
        assertThat((List<?>) JsonPath.read(ok("GET", API + "/cobranza/por-cobrar?clienteId=" + clienteId, null),
                "$[?(@.ventaId == " + ventaId + ")]")).as("saldo 0: ya no está por cobrar").isEmpty();

        // 8. Seguridad: sin token 401; la asistente sin permiso no anula (403)
        enviar("PATCH", API + "/ventas/" + ventaId + "/anular", null, null).andExpect(status().isUnauthorized());
        enviar("PATCH", API + "/ventas/" + ventaId + "/anular", null, "ASISTENTE").andExpect(status().isForbidden());

        // 9. El ADMIN anula: vuelve el stock, se cancelan las visitas y queda una devolución por lo pagado
        String anulada = ok("PATCH", API + "/ventas/" + ventaId + "/anular", null);
        assertThat((String) JsonPath.read(anulada, "$.estado")).isEqualTo("ANULADA");
        assertThat(stock(material)).isEqualTo(10);
        assertThat(visitasDeLaVenta(ventaId)).extracting(m -> m.get("estado")).containsOnly("CANCELADO");
        List<Number> porDevolver = JsonPath.read(ok("GET", API + "/cobranza/devoluciones", null),
                "$[?(@.ventaId == " + ventaId + ")].monto");
        assertThat(new BigDecimal(porDevolver.get(0).toString())).isEqualByComparingTo(total);
        enviar("PATCH", API + "/ventas/" + ventaId + "/anular", null, "ADMIN").andExpect(status().isConflict());

        // 10. Auditoría: quién hizo qué, sobre qué venta, incluidos los intentos rechazados
        List<String> acciones = jdbc.queryForList(
                "SELECT accion || ' | ' || resultado FROM historial_cambios WHERE usuario = ? ORDER BY id", String.class, USUARIO);
        assertThat(acciones).anySatisfy(a -> assertThat(a).contains("venta #" + ventaId).contains("EXITO"));
        assertThat(acciones).anySatisfy(a -> assertThat(a).contains("venta #" + ventaId).contains("DENEGADO"));
    }
}
