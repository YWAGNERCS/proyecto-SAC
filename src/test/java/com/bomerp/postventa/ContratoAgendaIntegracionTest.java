package com.bomerp.postventa;

import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.catalogo.producto.service.ProductoService;
import com.bomerp.clientes.cliente.service.ClienteService;
import com.bomerp.clientes.general.integration.Cliente;
import com.bomerp.clientes.sede.dto.SedeDTO;
import com.bomerp.clientes.sede.service.SedeClienteService;
import com.bomerp.config.ReglaNegocioException;
import com.bomerp.postventa.contrato.dto.ContratoDTO;
import com.bomerp.postventa.contrato.service.ContratoMantenimientoService;
import com.bomerp.postventa.mantenimiento.service.MantenimientoService;
import com.bomerp.servicios.orden.dto.OrdenDTO;
import com.bomerp.servicios.orden.entity.OrdenTrabajo;
import com.bomerp.servicios.orden.service.OrdenTrabajoService;
import com.bomerp.seguridad.permisos.KeycloakAdminClient;
import com.bomerp.ventas.venta.entity.Venta;
import com.bomerp.ventas.venta.service.VentaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:1/keys",
        "app.facturacion.modo=simulado",
        "spring.mail.username=",
        "app.notificaciones.correo-oficina="
})
@Transactional
class ContratoAgendaIntegracionTest {
    @Autowired ClienteService clientes;
    @Autowired SedeClienteService sedes;
    @Autowired ContratoMantenimientoService contratos;
    @Autowired MantenimientoService mantenimientos;
    @Autowired OrdenTrabajoService ordenes;
    @Autowired ProductoService productos;
    @Autowired VentaService ventas;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean KeycloakAdminClient keycloak;

    @DynamicPropertySource
    static void configurarBase(DynamicPropertyRegistry propiedades) {
        String oracleLocal = System.getenv("AGENDA_PRUEBA_ORACLE_LOCAL");
        if ("1".equals(oracleLocal)) {
            propiedades.add("spring.datasource.url", () -> "jdbc:oracle:thin:@localhost:41521/FREEPDB1");
            propiedades.add("spring.datasource.username", () -> "jd_app");
            propiedades.add("spring.datasource.password", () -> System.getenv("AGENDA_PRUEBA_ORACLE_CLAVE"));
            propiedades.add("spring.jpa.properties.hibernate.default_schema", () -> "JDREFRIGERACION");
            propiedades.add("spring.datasource.hikari.connection-init-sql", () -> "ALTER SESSION SET CURRENT_SCHEMA = JDREFRIGERACION");
            propiedades.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        } else {
            propiedades.add("spring.datasource.url", () -> "jdbc:h2:mem:contrato-agenda;MODE=Oracle;DB_CLOSE_DELAY=-1");
            propiedades.add("spring.datasource.username", () -> "sa");
            propiedades.add("spring.datasource.password", () -> "");
            propiedades.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        }
    }

    private Long cliente() {
        Cliente cliente = new Cliente();
        cliente.setTipo(Cliente.TipoCliente.PERSONA_NATURAL);
        cliente.setDni("12345678");
        cliente.setNombreORazonSocial("Cliente de prueba");
        return clientes.guardar(cliente).getId();
    }

    private ContratoDTO.Response contrato(Long cliente, LocalDate inicio) {
        return contrato(cliente, inicio, Venta.Moneda.SOLES);
    }

    private ContratoDTO.Response contrato(Long cliente, LocalDate inicio, Venta.Moneda moneda) {
        Long sede = sedes.crear(cliente, new SedeDTO.Request("Sede de prueba", "Av. Prueba 123", null, null)).id();
        return contratos.crear(new ContratoDTO.Request(cliente, sede, inicio, inicio.plusYears(1),
                3, new BigDecimal("150.00"), moneda, "Mantenimiento preventivo"), "prueba");
    }

    private OrdenDTO.Request orden(Long cliente, Long visita, LocalDate fecha) {
        return new OrdenDTO.Request(OrdenTrabajo.Tipo.MANTENIMIENTO, cliente, null, null, visita, null,
                fecha, "09:00", 60, "Técnico de prueba", null, "Mantenimiento preventivo");
    }

    @Test
    void generaCuatroVisitasYConservaElDiaOriginalCuandoUnMesEsMasCorto() {
        Long cliente = cliente();
        LocalDate inicio = LocalDate.of(LocalDate.now().plusYears(1).getYear(), 1, 31);
        var contrato = contrato(cliente, inicio);
        var visitas = mantenimientos.agenda("TODOS", 365, cliente);
        assertThat(contrato.clienteId()).isEqualTo(cliente);
        assertThat(contrato.sedeId()).isNotNull();
        assertThat(contrato.sedeNombre()).isEqualTo("Sede de prueba");
        assertThat(contrato.fechaInicio()).isEqualTo(inicio);
        assertThat(contrato.fechaFin()).isEqualTo(inicio.plusYears(1));
        assertThat(contrato.frecuenciaMeses()).isEqualTo(3);
        assertThat(contrato.montoVisita()).isEqualByComparingTo("150.00");
        assertThat(contrato.visitasPendientes()).isEqualTo(4);
        assertThat(visitas).extracting(v -> v.fechaProgramada())
                .containsExactly(inicio, inicio.plusMonths(3), inicio.plusMonths(6), inicio.plusMonths(9));
        assertThat(visitas).allMatch(v -> contrato.id().equals(v.contratoId()) && "Sede de prueba".equals(v.sedeNombre()));
    }

    @Test
    void sincronizaAmbasAgendasYCancelaLasOrdenesPendientesAlCancelarContrato() {
        Long cliente = cliente();
        LocalDate inicio = LocalDate.now();
        var contrato = contrato(cliente, inicio);
        var visitas = mantenimientos.agenda("TODOS", 365, cliente);
        Long primera = visitas.getFirst().id();
        Long segunda = visitas.get(1).id();
        var hecha = ordenes.crear(orden(cliente, primera, inicio), "prueba");
        var pendiente = ordenes.crear(orden(cliente, segunda, inicio.plusMonths(3)), "prueba");
        assertThat(hecha.sedeId()).isEqualTo(contrato.sedeId());
        assertThat(hecha.direccion()).isEqualTo("Av. Prueba 123");
        assertThat(hecha.mantenimientoId()).isEqualTo(primera);
        var primeraProgramada = mantenimientos.agenda("TODOS", 365, cliente).getFirst();
        assertThat(primeraProgramada.tecnico()).isEqualTo(hecha.tecnico());
        assertThat(primeraProgramada.ordenTrabajoId()).isEqualTo(hecha.id());
        LocalDate nuevaFecha = inicio.plusMonths(3).plusDays(1);
        mantenimientos.reprogramar(segunda, nuevaFecha, "Pedido del cliente");
        assertThat(ordenes.agenda(nuevaFecha, nuevaFecha)).extracting(OrdenDTO.Response::id).containsExactly(pendiente.id());
        LocalDate fechaOtra = nuevaFecha.plusDays(1);
        ordenes.actualizar(pendiente.id(), orden(cliente, segunda, fechaOtra));
        assertThat(mantenimientos.agenda("TODOS", 365, cliente).get(1).fechaProgramada()).isEqualTo(fechaOtra);
        ordenes.cerrar(hecha.id(), new OrdenDTO.Cierre(inicio, "Equipo revisado", "Cliente", null, List.of(), List.of()), "prueba");
        Producto servicio = new Producto();
        servicio.setNombre("Mano de obra de prueba");
        servicio.setSector(Producto.Sector.SERVICIO);
        servicio.setClasificacionDetraccion(com.bomerp.catalogo.producto.entity.ClasificacionDetraccion.MANTENIMIENTO_REPARACION);
        servicio.setMoneda(Producto.Moneda.SOLES);
        servicio.setCostoBase(new BigDecimal("150.00"));
        servicio.setMargenPorcentaje(BigDecimal.ZERO);
        Long servicioId = productos.guardar(servicio).getId();
        var factura = new OrdenDTO.Facturacion(List.of(new OrdenDTO.ItemFactura(servicioId, 1, BigDecimal.ZERO)),
                Venta.TipoPago.CONTADO, null, Venta.Moneda.SOLES, new BigDecimal("3.70"));
        assertThat(ordenes.facturar(hecha.id(), factura, "prueba", "Prueba").ventaFacturadaId()).isNotNull();
        contratos.cancelar(contrato.id(), "El cliente canceló el plan");
        var finales = mantenimientos.agenda("TODOS", 365, cliente);
        assertThat(finales.getFirst().ordenTrabajoId()).isEqualTo(hecha.id());
        assertThat(finales.getFirst().estado()).isEqualTo("REALIZADO");
        assertThat(finales.getFirst().fechaRealizada()).isEqualTo(inicio);
        assertThat(finales.getFirst().contratoId()).isEqualTo(contrato.id());
        assertThat(ordenes.obtener(hecha.id()).trabajoRealizado()).isEqualTo("Equipo revisado");
        assertThat(ordenes.obtener(pendiente.id()).estado()).isEqualTo("CANCELADA");
        assertThat(ordenes.agenda(fechaOtra, fechaOtra)).isEmpty();
        assertThat(contratos.obtener(contrato.id()).visitasPendientes()).isZero();
        assertThat(contratos.obtener(contrato.id()).visitasRealizadas()).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(Venta.Moneda.class)
    void laVistaPreviaYLaVentaUsanElMontoYMonedaDelContrato(Venta.Moneda moneda) {
        Long cliente = cliente();
        LocalDate inicio = LocalDate.now();
        var contrato = contrato(cliente, inicio, moneda);
        Long visita = mantenimientos.agenda("TODOS", 365, cliente).getFirst().id();
        var orden = ordenes.crear(orden(cliente, visita, inicio), "prueba");
        var cerrada = ordenes.cerrar(orden.id(),
                new OrdenDTO.Cierre(inicio, "Equipo revisado", "Cliente", null, List.of(), List.of()), "prueba");
        var tarifa = cerrada.tarifaContrato();
        assertThat(tarifa.contratoId()).isEqualTo(contrato.id());
        assertThat(tarifa.moneda()).isEqualTo(moneda);
        assertThat(tarifa.montoSinIgv()).isEqualByComparingTo("150.00");
        assertThat(tarifa.totalConIgv()).isEqualByComparingTo("177.00");
        assertThat(ordenes.obtener(orden.id()).tarifaContrato()).isEqualTo(tarifa);
        Producto servicio = new Producto();
        servicio.setNombre("Mano de obra con precio de catálogo");
        servicio.setSector(Producto.Sector.SERVICIO);
        servicio.setClasificacionDetraccion(com.bomerp.catalogo.producto.entity.ClasificacionDetraccion.MANTENIMIENTO_REPARACION);
        servicio.setMoneda(Producto.Moneda.SOLES);
        servicio.setCostoBase(new BigDecimal("180.00"));
        servicio.setMargenPorcentaje(BigDecimal.ZERO);
        Long servicioId = productos.guardar(servicio).getId();
        var factura = new OrdenDTO.Facturacion(List.of(new OrdenDTO.ItemFactura(servicioId, 1, BigDecimal.ZERO)),
                Venta.TipoPago.CREDITO, 30, Venta.Moneda.SOLES, new BigDecimal("3.70"));
        var facturada = ordenes.facturar(orden.id(), factura, "prueba", "Prueba");
        var venta = ventas.obtenerPorIdPublico(facturada.ventaFacturadaId());

        assertThat(contrato.montoVisita()).isEqualByComparingTo("150.00");
        assertThat(venta.getMoneda()).isEqualTo(moneda);
        assertThat(venta.calcularSubtotal()).isEqualByComparingTo(tarifa.montoSinIgv());
        assertThat(venta.calcularTotal()).isEqualByComparingTo(tarifa.totalConIgv());
        assertThat(productos.obtenerPorId(servicioId).calcularPrecioVenta()).isEqualByComparingTo("180.00");
        assertThat(venta.getEstadoPago()).isEqualTo(Venta.EstadoPago.PENDIENTE);
        assertThat(venta.getFechaLimitePago()).isEqualTo(inicio.plusDays(30));
        assertThatThrownBy(() -> ordenes.facturar(orden.id(), factura, "prueba", "Prueba"))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("ya se facturó");
    }

    @Test
    void unaOrdenSinContratoConservaElPrecioDelCatalogoAlVolverAFacturar() {
        Long cliente = cliente();
        LocalDate hoy = LocalDate.now();
        var orden = ordenes.crear(orden(cliente, null, hoy), "prueba");
        var cerrada = ordenes.cerrar(orden.id(),
                new OrdenDTO.Cierre(hoy, "Equipo revisado", "Cliente", null, List.of(), List.of()), "prueba");
        assertThat(cerrada.tarifaContrato()).isNull();
        Producto servicio = new Producto();
        servicio.setNombre("Mano de obra de catálogo");
        servicio.setSector(Producto.Sector.SERVICIO);
        servicio.setClasificacionDetraccion(com.bomerp.catalogo.producto.entity.ClasificacionDetraccion.MANTENIMIENTO_REPARACION);
        servicio.setMoneda(Producto.Moneda.SOLES);
        servicio.setCostoBase(new BigDecimal("180.00"));
        servicio.setMargenPorcentaje(BigDecimal.ZERO);
        Long servicioId = productos.guardar(servicio).getId();
        var factura = new OrdenDTO.Facturacion(List.of(new OrdenDTO.ItemFactura(servicioId, 1, BigDecimal.ZERO)),
                Venta.TipoPago.CREDITO, 30, Venta.Moneda.SOLES, new BigDecimal("3.70"));
        var facturada = ordenes.facturar(orden.id(), factura, "prueba", "Prueba");
        Long ventaOriginal = facturada.ventaFacturadaId();
        assertThat(ventas.obtenerPorIdPublico(ventaOriginal).calcularTotal()).isEqualByComparingTo("212.40");

        ventas.anular(ventaOriginal, "Prueba de refacturación", "prueba");
        var pendiente = ordenes.obtener(orden.id());
        assertThat(pendiente.ventaFacturadaAnulada()).isTrue();
        assertThat(pendiente.ventasAnuladas()).containsExactly(ventaOriginal);

        var reemplazo = ordenes.facturar(orden.id(), factura, "prueba", "Prueba");
        assertThat(reemplazo.ventaFacturadaId()).isNotEqualTo(ventaOriginal);
        assertThat(reemplazo.ventaFacturadaAnulada()).isFalse();
        assertThat(ordenes.obtener(orden.id()).ventasAnuladas()).containsExactly(ventaOriginal);
        assertThat(ventas.obtenerPorIdPublico(ventaOriginal).getEstado()).isEqualTo(Venta.EstadoVenta.ANULADA);
        assertThat(ventas.obtenerPorIdPublico(reemplazo.ventaFacturadaId()).getOrdenTrabajoOrigenId()).isEqualTo(orden.id());
        assertThatThrownBy(() -> ordenes.facturar(orden.id(), factura, "prueba", "Prueba"))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("ya se facturó");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void soloUnaReservaConcurrenteObtieneElMismoHorario() throws Exception {
        assumeFalse("1".equals(System.getenv("AGENDA_PRUEBA_ORACLE_LOCAL")), "Esta prueba confirma y limpia datos solo en H2.");
        Long cliente = cliente();
        LocalDate fecha = LocalDate.now().plusDays(2);
        String tecnico = "Técnico prueba concurrente " + cliente;
        var inicio = new CyclicBarrier(2);
        Callable<Boolean> reservar = () -> {
            inicio.await(5, TimeUnit.SECONDS);
            try {
                ordenes.crear(new OrdenDTO.Request(OrdenTrabajo.Tipo.MANTENIMIENTO, cliente, null, null, null, null,
                        fecha, "09:00", 60, tecnico, null, "Prueba de agenda"), "prueba");
                return true;
            } catch (ReglaNegocioException conflicto) {
                assertThat(conflicto).hasMessageContaining("ya tiene la orden");
                return false;
            }
        };

        try (var executor = Executors.newFixedThreadPool(2)) {
            var primera = executor.submit(reservar);
            var segunda = executor.submit(reservar);
            assertThat(List.of(primera.get(20, TimeUnit.SECONDS), segunda.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(ordenes.agenda(fecha, fecha)).filteredOn(orden -> tecnico.equals(orden.tecnico())).hasSize(1);
        } finally {
            jdbc.update("DELETE FROM ORDENES_TRABAJO WHERE CLIENTE_ID = ?", cliente);
            clientes.eliminar(cliente);
        }
    }

    @Test
    void rechazaOtraOrdenActivaParaLaMismaVisita() {
        Long cliente = cliente();
        LocalDate inicio = LocalDate.now();
        contrato(cliente, inicio);
        Long visita = mantenimientos.agenda("TODOS", 365, cliente).getFirst().id();
        ordenes.crear(orden(cliente, visita, inicio), "prueba");
        assertThatThrownBy(() -> ordenes.crear(orden(cliente, visita, inicio.plusDays(1)), "prueba"))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("ya tiene una orden programada");
    }
}
