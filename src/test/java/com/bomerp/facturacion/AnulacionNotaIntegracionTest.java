package com.bomerp.facturacion;
import com.bomerp.clientes.general.integration.Cliente;
import com.bomerp.clientes.cliente.repository.ClienteRepository;
import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.catalogo.producto.repository.ProductoRepository;
import com.bomerp.ventas.venta.entity.Venta;
import com.bomerp.ventas.venta.service.VentaService;
import com.bomerp.facturacion.comprobante.entity.Comprobante;
import com.bomerp.facturacion.comprobante.repository.ComprobanteRepository;
import com.bomerp.facturacion.notacredito.repository.NotaCreditoRepository;
import com.bomerp.facturacion.pdf.service.NotaCreditoPdfService;
import com.bomerp.facturacion.ose.integration.OseClient;
import com.bomerp.cobranza.devolucion.service.DevolucionService;
import com.bomerp.cobranza.cobro.service.CobranzaService;
import com.bomerp.seguridad.permisos.KeycloakAdminClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:anulacion-nota;MODE=Oracle;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.properties.hibernate.default_schema=","spring.sql.init.mode=never",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:1/keys","spring.mail.username=","app.facturacion.modo=simulado",
        "app.empresa.cuenta-detracciones=BN-PRUEBA-022","app.tributos.medio-pago-detraccion=003"})
@Transactional
class AnulacionNotaIntegracionTest {
    @Autowired ClienteRepository clientes;@Autowired ProductoRepository productos;@Autowired VentaService ventas;
    @Autowired ComprobanteRepository comprobantes;@Autowired NotaCreditoRepository notas;@Autowired NotaCreditoPdfService pdf;
    @Autowired DevolucionService devoluciones;@Autowired CobranzaService cobranza;
    @MockitoBean OseClient ose;@MockitoBean KeycloakAdminClient keycloak;
    @Test void emitirElModelo022ConservaCuentaMedioImporteYCredito() {
        var c=new Cliente();c.setTipo(Cliente.TipoCliente.EMPRESA);c.setNombreORazonSocial("Cliente prueba modelo 022");c.setRuc("20100066603");clientes.save(c);
        var p=new Producto();p.setNombre("Servicio prueba modelo 022");p.setSector(Producto.Sector.SERVICIO);
        p.setClasificacionDetraccion(com.bomerp.catalogo.producto.entity.ClasificacionDetraccion.OTROS_SERVICIOS_EMPRESARIALES);
        p.setCostoBase(new BigDecimal("720"));p.setMargenPorcentaje(BigDecimal.ZERO);p.setMoneda(Producto.Moneda.SOLES);productos.save(p);
        var v=ventas.registrarVentaDirecta(c.getId(),List.of(new VentaService.ItemVentaDTO(p.getId(),1,BigDecimal.ZERO)),Venta.TipoPago.CREDITO,61,Venta.Moneda.SOLES,new BigDecimal("3.75"),"prueba","prueba",null);
        var emitido=comprobantes.findByVentaIdAndReemplazadoFalse(v.id()).orElseThrow();
        assertThat(emitido.getMedioPagoDetraccion()).isEqualTo("003");assertThat(emitido.getCuentaDetraccionesEmision()).isEqualTo("BN-PRUEBA-022");
        assertThat(emitido.getVenta().getCodigoDetraccion()).isEqualTo("022");assertThat(emitido.getTotal()).isEqualByComparingTo("849.60");
        assertThat(emitido.getVenta().getMontoDeduccion()).isEqualByComparingTo("102.00");
        assertThat(emitido.getVenta().calcularNetoACobrar()).isEqualByComparingTo("747.60");
        assertThat(cobranza.porCobrar(c.getId()).getFirst().netoPorCobrar()).isEqualByComparingTo("747.60");
    }
    @Test void anulacionConservaDocumentoRestauraStockYDejaDevolucionPendiente() {
        var c=new Cliente();c.setTipo(Cliente.TipoCliente.EMPRESA);c.setNombreORazonSocial("Cliente prueba nota");c.setRuc("20100066603");clientes.save(c);
        var p=new Producto();p.setNombre("Material prueba nota");p.setSector(Producto.Sector.MATERIAL_SERVICIO);p.setCostoBase(new BigDecimal("100"));p.setMargenPorcentaje(BigDecimal.ZERO);p.setMoneda(Producto.Moneda.SOLES);p.setStockActual(10);productos.save(p);
        var v=ventas.registrarVentaDirecta(c.getId(),List.of(new VentaService.ItemVentaDTO(p.getId(),2,BigDecimal.ZERO)),Venta.TipoPago.CONTADO,null,Venta.Moneda.SOLES,new BigDecimal("3.75"),"prueba","prueba",null);
        var original=comprobantes.findByVentaIdAndReemplazadoFalse(v.id()).orElseThrow();original.setEstadoSunat(Comprobante.EstadoSunat.ACEPTADO);original.registrarAmbiente(Comprobante.AmbienteEmision.PRODUCCION);comprobantes.saveAndFlush(original);
        assertThat(productos.findById(p.getId()).orElseThrow().getStockActual()).isEqualTo(8);
        ventas.anular(v.id(),"Anulación pedida por el cliente","prueba");
        assertThat(productos.findById(p.getId()).orElseThrow().getStockActual()).isEqualTo(10);
        var n=notas.findByComprobanteReferencia_Id(original.getId()).orElseThrow();
        assertThat(n.getTotal()).isEqualByComparingTo(v.total());assertThat(n.getNumeroComprobanteReferencia()).isEqualTo(original.getSerie()+"-"+original.getNumero());
        assertThat(pdf.descargar(n.getId())).startsWith("%PDF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        assertThat(devoluciones.listar(null,null)).isNotEmpty();
        assertThat(cobranza.porCobrar(c.getId())).isEmpty();
        assertThatThrownBy(()->ventas.anular(v.id(),"Segundo intento de anulación","prueba")).hasMessageContaining("ANULADA");
        assertThat(notas.count()).isEqualTo(1);assertThat(productos.findById(p.getId()).orElseThrow().getStockActual()).isEqualTo(10);
    }
}
