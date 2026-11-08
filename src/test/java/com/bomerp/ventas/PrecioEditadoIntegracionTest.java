package com.bomerp.ventas;

import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.catalogo.producto.repository.ProductoRepository;
import com.bomerp.compras.compra.dto.CompraRequestDTO;
import com.bomerp.compras.compra.entity.Compra;
import com.bomerp.compras.compra.service.CompraService;
import com.bomerp.proformas.proforma.entity.Proforma;
import com.bomerp.proformas.proforma.service.ProformaService;
import com.bomerp.proformas.proformarequest.entity.ProformaRequest;
import com.bomerp.ventas.venta.entity.Venta;
import com.bomerp.ventas.venta.service.VentaService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Auditoría M1 y M2 contra Oracle: la compra separa el IGV y deja el último costo (sin IGV) en el producto, sin
 * tocar su precio; la venta usa ese costo para la rentabilidad; el precio de una línea se puede subir (no bajar)
 * con motivo y permiso, y la línea guarda las dos cifras; el precio editado en la proforma pasa a la venta.
 * Todo se deshace al terminar.
 */
// Necesita Oracle con los datos de desarrollo: la integración continua la salta (-DexcludedGroups=oracle)
@Tag("oracle")
@SpringBootTest(properties = "app.facturacion.reintento-inicial-ms=86400000")
@Transactional
class PrecioEditadoIntegracionTest {

    @Autowired private VentaService ventaService;
    @Autowired private CompraService compraService;
    @Autowired private ProformaService proformaService;
    @Autowired private ProductoRepository productoRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;

    private Long clienteId;
    private Long proveedorId;
    private Long servicioId;

    @BeforeEach
    void preparar() {
        clienteId = jdbc.queryForObject("SELECT MIN(id) FROM clientes WHERE agente_retencion = 0", Long.class);
        proveedorId = jdbc.queryForObject("SELECT MIN(id) FROM proveedores", Long.class);
        Producto servicio = new Producto();
        servicio.setNombre("Gas de prueba (precio editado)");
        servicio.setSector(Producto.Sector.SERVICIO);
        servicio.setCostoBase(new BigDecimal("500.00"));
        servicio.setMargenPorcentaje(new BigDecimal("5"));      // precio de catálogo 525.00
        servicio.setMoneda(Producto.Moneda.SOLES);
        servicioId = productoRepository.save(servicio).getId();
    }

    @AfterEach
    void cerrarSesion() {
        SecurityContextHolder.clearContext();
    }

    private void comoUsuario(String... roles) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("lucy", null, roles));
    }

    private Long vender(BigDecimal precio, String motivo) {
        Long id = ventaService.registrarVentaDirecta(clienteId,
                List.of(new VentaService.ItemVentaDTO(servicioId, 1, BigDecimal.ZERO, precio, motivo)),
                Venta.TipoPago.CONTADO, null, Venta.Moneda.SOLES, null, "lucy-id", "lucy", null, null).id();
        em.flush();
        return id;
    }

    private Map<String, Object> linea(Long ventaId) {
        return jdbc.queryForMap("SELECT precio_unitario, precio_catalogo, precio_editado_por, motivo_precio, costo_unitario "
                + "FROM detalle_venta WHERE venta_id = ?", ventaId);
    }

    @Test
    void laCompraSeparaElIgvYGuardaElUltimoCostoSinTocarElPrecio() {
        comoUsuario("ROLE_ADMIN");
        String factura = "F999-" + (System.nanoTime() % 100_000_000L);
        Compra compra = compraService.registrarCompra(new CompraRequestDTO(proveedorId, LocalDate.now(), factura,
                Producto.Moneda.SOLES, null, null, null,
                List.of(new CompraRequestDTO.DetalleCompraRequestDTO(servicioId, 2, new BigDecimal("660.80")))), "lisandro");
        em.flush();

        // 2 × 660.80 = 1321.60 con IGV → 1120.00 + 201.60
        assertThat(compra.getTotal()).isEqualByComparingTo("1321.60");
        assertThat(compra.getSubtotal()).isEqualByComparingTo("1120.00");
        assertThat(compra.getIgv()).isEqualByComparingTo("201.60");

        Producto producto = productoRepository.findById(servicioId).orElseThrow();
        assertThat(producto.getUltimoCostoCompra()).isEqualByComparingTo("560.00");   // 660.80 / 1.18
        assertThat(producto.getFechaUltimaCompra()).isEqualTo(LocalDate.now());
        assertThat(producto.calcularPrecioVenta()).isEqualByComparingTo("525.00");    // el catálogo no cambia

        // La venta guarda el costo de la última compra: la rentabilidad muestra la pérdida real
        Long ventaId = vender(null, null);
        assertThat((BigDecimal) linea(ventaId).get("COSTO_UNITARIO")).isEqualByComparingTo("560.00");
        assertThat(linea(ventaId).get("PRECIO_CATALOGO")).isNull();
    }

    @Test
    void conPermisoSubeElPrecioConMotivoYGuardaLasDosCifras() {
        comoUsuario("ROLE_ASISTENTE", "ROLE_PERM_EDITAR_PRECIO");
        Long ventaId = vender(new BigDecimal("560"), "  Subió el costo del proveedor ");

        Map<String, Object> l = linea(ventaId);
        assertThat((BigDecimal) l.get("PRECIO_UNITARIO")).isEqualByComparingTo("560.00");
        assertThat((BigDecimal) l.get("PRECIO_CATALOGO")).isEqualByComparingTo("525.00");
        assertThat(l.get("PRECIO_EDITADO_POR")).isEqualTo("lucy");
        assertThat(l.get("MOTIVO_PRECIO")).isEqualTo("Subió el costo del proveedor");
        // El total de la venta sale del precio editado: 560 + 18 % = 660.80
        assertThat(jdbc.queryForObject("SELECT total FROM comprobantes WHERE venta_id = ? AND reemplazado = FALSE",
                BigDecimal.class, ventaId)).isEqualByComparingTo("660.80");
    }

    @Test
    void sinPermisoOParaBajarloOSinMotivoNoSeRegistra() {
        int antes = jdbc.queryForObject("SELECT COUNT(*) FROM ventas", Integer.class);

        comoUsuario("ROLE_ASISTENTE");
        assertThatThrownBy(() -> vender(new BigDecimal("560"), "Subió el costo"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("No tiene permiso para editar precios");

        comoUsuario("ROLE_ADMIN");
        assertThatThrownBy(() -> vender(new BigDecimal("500"), "Cliente frecuente"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("use el descuento");
        assertThatThrownBy(() -> vender(new BigDecimal("560"), "  "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("por qué cambia el precio");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ventas", Integer.class)).isEqualTo(antes);

        // El mismo precio de catálogo no es una edición: no pide permiso ni motivo
        comoUsuario("ROLE_ASISTENTE");
        assertThat(linea(vender(new BigDecimal("525.00"), null)).get("PRECIO_EDITADO_POR")).isNull();
    }

    @Test
    void elPrecioEditadoEnLaProformaPasaALaVenta() {
        comoUsuario("ROLE_ADMIN");
        Long proformaId = proformaService.crear(new ProformaRequest.CrearProformaDTO(clienteId, Proforma.Moneda.SOLES, 15,
                List.of(new ProformaRequest.ItemDTO(servicioId, 1, BigDecimal.ZERO, new BigDecimal("550"), "Precio pactado con el cliente")),
                null)).id();
        em.flush();
        assertThat(jdbc.queryForMap("SELECT precio_unitario, precio_catalogo, precio_editado_por FROM items_proforma "
                + "WHERE proforma_id = ?", proformaId).get("PRECIO_EDITADO_POR")).isEqualTo("lucy");

        proformaService.aceptar(proformaId);
        Long ventaId = ventaService.convertirProformaEnVenta(proformaId, Venta.TipoPago.CONTADO, null, "lucy-id", "lucy").id();
        em.flush();

        Map<String, Object> l = linea(ventaId);
        assertThat((BigDecimal) l.get("PRECIO_UNITARIO")).isEqualByComparingTo("550.00");
        assertThat((BigDecimal) l.get("PRECIO_CATALOGO")).isEqualByComparingTo("525.00");
        assertThat(l.get("MOTIVO_PRECIO")).isEqualTo("Precio pactado con el cliente");
    }
}
