package com.bomerp.facturacion.sunat.integration;

import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.clientes.general.integration.Cliente;
import com.bomerp.config.empresa.EmpresaProperties;
import com.bomerp.facturacion.comprobante.entity.Comprobante;
import com.bomerp.facturacion.notacredito.entity.NotaCredito;
import com.bomerp.facturacion.ose.integration.OseClient;
import com.bomerp.ventas.detalleventa.entity.DetalleVenta;
import com.bomerp.ventas.venta.entity.Venta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Envío real al ambiente BETA de SUNAT (pruebas, sin efecto tributario): usuario MODDATOS y el
 * certificado de prueba de certificados/beta-prueba.p12. Necesita internet, por eso no corre en un
 * "mvn test" normal: activarlo con la variable SUNAT_BETA=1 (la clave del certificado se lee de .env).
 */
class SunatBetaTest {

    private static final String BETA = "https://e-beta.sunat.gob.pe/ol-ti-itcpfegem-beta/billService";
    private static SunatDirectoClient cliente;

    @BeforeAll
    static void preparar() throws Exception {
        assumeTrue("1".equals(System.getenv("SUNAT_BETA")), "Solo con SUNAT_BETA=1");
        String clave = Files.readAllLines(Path.of(".env")).stream()
                .filter(l -> l.startsWith("SUNAT_CERT_CLAVE=")).map(l -> l.substring(17).trim()).findFirst().orElseThrow();
        EmpresaProperties empresa = new EmpresaProperties("JD REFRIGERACIÓN S.A.C.", "20521632268",
                "Jr. Jorge Chávez N° 184 / 206, Breña, Lima", "00-000-123456");
        cliente = new SunatDirectoClient(new SunatProperties(BETA, "MODDATOS", "MODDATOS", "certificados/beta-prueba.p12", clave), empresa);
    }

    @Test
    void facturaAceptadaPorSunatBeta() {
        Comprobante factura = comprobante(Comprobante.TipoComprobante.FACTURA, "F001", 1);
        OseClient.RespuestaOse r = cliente.emitir(factura);
        System.out.println("FACTURA F001-1 → " + r.resultado() + " " + r.codigo() + " " + r.mensaje());
        assertThat(r.exito()).as(r.mensaje()).isTrue();
    }

    @Test
    void boletaIndividualAceptadaPorSunatBeta() {
        Comprobante boleta = comprobante(Comprobante.TipoComprobante.BOLETA, "B001", 1);
        boleta.getVenta().getCliente().setTipo(Cliente.TipoCliente.PERSONA_NATURAL);
        boleta.getVenta().getCliente().setRuc(null);
        boleta.getVenta().getCliente().setDni("45678912");
        boleta.getVenta().getCliente().setNombreORazonSocial("CLIENTE DE PRUEBA");
        OseClient.RespuestaOse r = cliente.emitir(boleta);
        System.out.println("BOLETA B001-1 → " + (r.resultado() + " " + r.codigo() + " " + r.mensaje()));
        assertThat(r.exito()).as(r.mensaje()).isTrue();
    }

    @Test
    void notaDeCreditoAceptadaPorSunatBeta() {
        NotaCredito nota = new NotaCredito();
        nota.setSerie("FC01");
        nota.setNumero("00000001");
        nota.setMotivo("Anulación de la operación");
        nota.setComprobanteReferencia(comprobante(Comprobante.TipoComprobante.FACTURA, "F001", 1));
        OseClient.RespuestaOse r = cliente.emitirNotaCredito(nota);
        System.out.println("NOTA DE CRÉDITO FC01-1 → " + (r.resultado() + " " + r.codigo() + " " + r.mensaje()));
        assertThat(r.exito()).as(r.mensaje()).isTrue();
    }

    /** Venta de prueba: 2 × filtro (S/ 50) + 1 servicio de mantenimiento (S/ 120), al contado. */
    static Comprobante comprobante(Comprobante.TipoComprobante tipo, String serie, int numero) {
        Cliente c = new Cliente();
        c.setTipo(Cliente.TipoCliente.EMPRESA);
        c.setNombreORazonSocial("EMPRESA DE PRUEBA S.A.C.");
        c.setRuc("20100066603");

        Venta v = new Venta();
        v.setCliente(c);
        v.setMoneda(Venta.Moneda.SOLES);
        v.setTipoPago(Venta.TipoPago.CONTADO);
        v.setDetalles(List.of(linea(v, "Filtro deshidratador 1/4", Producto.Sector.MATERIAL_SERVICIO, 2, "50.00"),
                linea(v, "Mantenimiento preventivo de aire acondicionado", Producto.Sector.SERVICIO, 1, "120.00")));

        Comprobante comp = new Comprobante();
        comp.setTipo(tipo);
        comp.setSerie(serie);
        comp.setNumero(String.format("%08d", numero));
        comp.setFechaEmision(LocalDate.now());
        comp.setVenta(v);
        comp.setTotal(v.calcularTotal());
        return comp;
    }

    private static DetalleVenta linea(Venta v, String nombre, Producto.Sector sector, int cantidad, String precio) {
        Producto p = new Producto();
        p.setNombre(nombre);
        p.setSector(sector);
        DetalleVenta d = new DetalleVenta();
        d.setVenta(v);
        d.setProducto(p);
        d.setCantidad(cantidad);
        d.setPrecioUnitario(new BigDecimal(precio));
        d.setDescuentoPorcentaje(BigDecimal.ZERO);
        return d;
    }
}
