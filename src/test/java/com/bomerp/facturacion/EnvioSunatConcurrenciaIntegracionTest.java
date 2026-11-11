package com.bomerp.facturacion;

import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.catalogo.producto.repository.ProductoRepository;
import com.bomerp.facturacion.comprobante.entity.Comprobante;
import com.bomerp.facturacion.facturacion.service.EnvioSunatService;
import com.bomerp.facturacion.ose.integration.OseClient;
import com.bomerp.facturacion.ose.integration.OseClient.RespuestaOse;
import com.bomerp.notificaciones.notificacion.service.NotificadorCorreo;
import com.bomerp.ventas.venta.entity.Venta;
import com.bomerp.ventas.venta.service.VentaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Envío y reenvío simultáneos de un mismo comprobante contra Oracle (SUNAT simulada: no sale nada a SUNAT).
 * Lo que se busca descartar es que el mismo comprobante llegue dos veces a SUNAT o que una respuesta de SUNAT ya
 * guardada (ACEPTADO) se pierda porque otra operación escribió encima con datos viejos.
 *
 * No es @Transactional: el envío corre en el hilo de la cola y necesita ver los datos confirmados. Lo que crea se borra.
 */
@Tag("oracle")
@SpringBootTest(properties = {
        "app.facturacion.reintento-inicial-ms=86400000",
        "app.facturacion.pausa-entre-envios-ms=0",
        "app.correo.revision-inicial-ms=86400000"})
class EnvioSunatConcurrenciaIntegracionTest {

    @MockitoBean private OseClient ose;
    @MockitoBean private NotificadorCorreo notificador;
    @Autowired private org.springframework.transaction.PlatformTransactionManager txManager;

    @Autowired private EnvioSunatService envioSunat;
    @Autowired private VentaService ventaService;
    @Autowired private ProductoRepository productos;
    @Autowired private JdbcTemplate jdbc;

    private static final String HILO_REENVIO = "reenvio-de-prueba";

    private Long clienteId;
    private Long productoId;
    private final List<Long> ventas = new ArrayList<>();
    private Map<String, Long> seriesAntes;
    private final AtomicInteger llamadasSunat = new AtomicInteger();

    @BeforeEach
    void preparar() {
        when(ose.disponible()).thenReturn(true);
        when(ose.ambiente()).thenReturn(Comprobante.AmbienteEmision.PRODUCCION);
        when(ose.conValidezTributaria()).thenReturn(true);
        clienteId = jdbc.queryForObject("SELECT MIN(id) FROM clientes WHERE agente_retencion = 0 AND correo IS NULL", Long.class);
        seriesAntes = new HashMap<>();
        jdbc.query("SELECT serie, ultimo_numero FROM series_comprobante", rs -> {
            seriesAntes.put(rs.getString(1), rs.getLong(2));
        });
        Producto p = new Producto();
        p.setNombre("Visita de prueba (envío simultáneo)");
        p.setSector(Producto.Sector.SERVICIO);
        p.setCostoBase(new BigDecimal("100.00"));
        p.setMargenPorcentaje(BigDecimal.ZERO);
        p.setMoneda(Producto.Moneda.SOLES);
        productoId = productos.save(p).getId();
    }

    @AfterEach
    void limpiar() {
        for (Long v : ventas) {
            jdbc.update("DELETE FROM intentos_correo WHERE envio_correo_id IN (SELECT e.id FROM envios_correo e "
                    + "JOIN comprobantes c ON c.id = e.comprobante_id WHERE c.venta_id = ?)", v);
            jdbc.update("DELETE FROM envios_correo WHERE comprobante_id IN (SELECT id FROM comprobantes WHERE venta_id = ?)", v);
            jdbc.update("DELETE FROM envios_sunat WHERE comprobante_id IN (SELECT id FROM comprobantes WHERE venta_id = ?)", v);
            jdbc.update("DELETE FROM comprobantes WHERE venta_id = ?", v);
            jdbc.update("DELETE FROM detalle_venta WHERE venta_id = ?", v);
            jdbc.update("DELETE FROM ventas WHERE id = ?", v);
        }
        jdbc.update("DELETE FROM productos WHERE id = ?", productoId);
        seriesAntes.forEach((serie, n) -> jdbc.update("UPDATE series_comprobante SET ultimo_numero = ? WHERE serie = ?", n, serie));
    }

    private static void esperar(BooleanSupplier condicion, String que) throws InterruptedException {
        long fin = System.currentTimeMillis() + 15000;
        while (!condicion.getAsBoolean()) {
            if (System.currentTimeMillis() > fin) throw new AssertionError("No pasó a tiempo: " + que);
            Thread.sleep(100);
        }
    }

    private Map<String, Object> fila(Long id) {
        return jdbc.queryForMap("SELECT estado_sunat, sunat_intentos, sunat_codigo FROM comprobantes WHERE id = ?", id);
    }

    private static RespuestaOse aceptado() {
        return new RespuestaOse(OseClient.Resultado.ACEPTADO, 0, "Aceptado (prueba)", null, null, new byte[]{1}, new byte[]{80, 75});
    }

    /** Venta cuyo primer envío no tuvo respuesta de SUNAT: queda PENDIENTE con 1 intento, como en un corte de red. */
    private Long pendienteTrasUnCorte() throws InterruptedException {
        doReturn(RespuestaOse.reintentar("SUNAT no respondió (prueba)", null)).when(ose).emitir(any());
        Long ventaId = ventaService.registrarVentaDirecta(clienteId,
                List.of(new VentaService.ItemVentaDTO(productoId, 1, BigDecimal.ZERO)),
                Venta.TipoPago.CONTADO, null, Venta.Moneda.SOLES, null, "prueba", "prueba", null, null).id();
        ventas.add(ventaId);
        esperar(() -> !jdbc.queryForList("SELECT 1 FROM comprobantes WHERE venta_id = ? AND sunat_intentos > 0",
                ventaId).isEmpty(), "primer envío de la venta " + ventaId);
        return jdbc.queryForObject("SELECT id FROM comprobantes WHERE venta_id = ?", Long.class, ventaId);
    }

    /**
     * El caso delicado: el reintento automático está esperando a SUNAT y, justo entonces, alguien pulsa "Reenviar".
     * El reenvío lee el comprobante (PENDIENTE) antes de que se guarde la respuesta de SUNAT y lo escribe después.
     * Antes del 11/11/2026 esa escritura tardía pisaba la respuesta: con un ACEPTADO lo frenaba (por casualidad) el
     * disparador del ambiente con un ORA-20031 para el usuario; con un RECHAZADO se perdía el rechazo, volvía a
     * PENDIENTE y se enviaba otra vez a SUNAT.
     */
    @Test
    void reenviarMientrasSunatAceptaNoPierdeLaAceptacionNiLoEnviaDosVeces() throws Exception {
        Long id = pendienteTrasUnCorte();
        Throwable error = reenviarMientrasSunatResponde(id, aceptado());
        assertThat(fila(id).get("ESTADO_SUNAT")).as("la aceptación de SUNAT no debe perderse").isEqualTo("ACEPTADO");
        assertThat(llamadasSunat.get()).as("SUNAT debe recibir el comprobante una sola vez").isEqualTo(1);
        assertThat(error).as("el reenvío no debe fallar con un error de Oracle").isNull();
    }

    @Test
    void reenviarMientrasSunatRechazaNoPierdeElRechazoNiLoEnviaDosVeces() throws Exception {
        Long id = pendienteTrasUnCorte();
        Throwable error = reenviarMientrasSunatResponde(id, new RespuestaOse(OseClient.Resultado.RECHAZADO, 2800,
                "El dato ingresado en el tipo de documento no corresponde (prueba)", null, null, new byte[]{1}, new byte[]{80, 75}));
        Map<String, Object> f = fila(id);
        assertThat(f.get("ESTADO_SUNAT")).as("el rechazo de SUNAT no debe perderse").isEqualTo("RECHAZADO");
        assertThat(((Number) f.get("SUNAT_CODIGO")).intValue()).isEqualTo(2800);
        assertThat(llamadasSunat.get()).as("SUNAT debe recibir el comprobante una sola vez").isEqualTo(1);
        assertThat(error).isNull();
    }

    /** Reenvío manual cruzado con el reintento automático; devuelve el error del reenvío, si hubo. */
    private Throwable reenviarMientrasSunatResponde(Long id, RespuestaOse respuesta) throws Exception {
        CountDownLatch reenvioLeyo = new CountDownLatch(1);
        CountDownLatch seguirReenvio = new CountDownLatch(1);
        AtomicReference<Throwable> errorReenvio = new AtomicReference<>();

        // "Reenviar" corre dentro de una transacción que la prueba mantiene abierta: lee y cambia el comprobante,
        // pero lo escribe recién al confirmar. Así se reproduce, sin trucos, el hueco entre leer y escribir.
        Thread reenvio = new Thread(() -> {
            try {
                new org.springframework.transaction.support.TransactionTemplate(txManager).executeWithoutResult(s -> {
                    envioSunat.reenviarComprobante(id);
                    reenvioLeyo.countDown();
                    try {
                        seguirReenvio.await(20, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            } catch (Throwable e) {
                reenvioLeyo.countDown();
                errorReenvio.set(e);   // un 409 "ya fue aceptado" también es una respuesta correcta
            }
        }, HILO_REENVIO);

        llamadasSunat.set(0);
        doAnswer(inv -> {
            if (llamadasSunat.incrementAndGet() == 1) {
                reenvio.start();                                   // "Reenviar" mientras SUNAT responde
                assertThat(reenvioLeyo.await(10, TimeUnit.SECONDS)).isTrue();
            }
            return respuesta;
        }).when(ose).emitir(any());

        envioSunat.programarComprobante(id);                       // el reintento automático
        esperar(() -> llamadasSunat.get() >= 1, "la llamada a SUNAT");
        Thread.sleep(1500);                                         // el reintento guarda (o espera el bloqueo)
        seguirReenvio.countDown();
        reenvio.join(20000);
        Thread.sleep(3000);                                         // si quedó otro envío en la cola, que corra
        Throwable e = errorReenvio.get();
        // Un 409 ("ya fue aceptado/rechazado") sería una respuesta válida del reenvío; un error de Oracle, no
        return e instanceof org.springframework.web.server.ResponseStatusException ? null : e;
    }

    /** Dos "Reenviar" a la vez (dos usuarios o doble clic): SUNAT lo recibe una vez. */
    @Test
    void dosReenviosSimultaneosLoEnvianUnaSolaVez() throws Exception {
        Long id = pendienteTrasUnCorte();
        llamadasSunat.set(0);
        doAnswer(inv -> {
            llamadasSunat.incrementAndGet();
            Thread.sleep(300);
            return aceptado();
        }).when(ose).emitir(any());

        ExecutorService dos = Executors.newFixedThreadPool(2);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<?>> f = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            f.add(dos.submit(() -> {
                salida.await();
                try {
                    envioSunat.reenviarComprobante(id);
                } catch (org.springframework.web.server.ResponseStatusException ya) {
                    // el segundo puede llegar cuando ya está aceptado: 409, correcto
                }
                return null;
            }));
        }
        salida.countDown();
        for (Future<?> x : f) x.get(20, TimeUnit.SECONDS);
        dos.shutdown();
        esperar(() -> "ACEPTADO".equals(fila(id).get("ESTADO_SUNAT")), "ACEPTADO");
        Thread.sleep(2000);

        assertThat(fila(id).get("ESTADO_SUNAT")).isEqualTo("ACEPTADO");
        assertThat(llamadasSunat.get()).isEqualTo(1);
    }

    /** El reintento automático y un envío programado del mismo comprobante a la vez: una sola llamada. */
    @Test
    void elReintentoAutomaticoYOtroEnvioDelMismoComprobanteNoLoDuplican() throws Exception {
        Long id = pendienteTrasUnCorte();
        llamadasSunat.set(0);
        doAnswer(inv -> {
            llamadasSunat.incrementAndGet();
            Thread.sleep(300);
            return aceptado();
        }).when(ose).emitir(any());

        envioSunat.programarComprobante(id);
        envioSunat.reintentarPendientes();
        envioSunat.programarComprobante(id);
        esperar(() -> "ACEPTADO".equals(fila(id).get("ESTADO_SUNAT")), "ACEPTADO");
        Thread.sleep(2000);

        assertThat(llamadasSunat.get()).isEqualTo(1);
    }
}
