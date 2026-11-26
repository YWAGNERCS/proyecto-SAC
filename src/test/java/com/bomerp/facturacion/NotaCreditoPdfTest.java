package com.bomerp.facturacion;
import com.bomerp.facturacion.pdf.service.NotaCreditoPdfService;
import com.bomerp.facturacion.comprobante.entity.Comprobante;
import com.bomerp.facturacion.notacredito.entity.NotaCredito;
import com.bomerp.config.empresa.EmpresaProperties;
import com.bomerp.ventas.venta.entity.Venta;
import com.bomerp.ventas.detalleventa.entity.DetalleVenta;
import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.clientes.general.integration.Cliente;
import com.lowagie.text.pdf.*;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
class NotaCreditoPdfTest {
    @Test void documentoIdentificaReferenciaMotivoYAmbienteSinCambiarElImporte() throws Exception {
        var cliente=new Cliente();cliente.setNombreORazonSocial("Cliente demo");var p=new Producto();p.setNombre("Mantenimiento de demostración");
        var v=new Venta();v.setCliente(cliente);v.setMoneda(Venta.Moneda.SOLES);v.getDetalles().add(new DetalleVenta(p,1,new BigDecimal("1000")));
        var c=new Comprobante();c.setVenta(v);c.setSerie("F001");c.setNumero("00000001");c.setReceptorNombre("Cliente de la factura original");c.setReceptorNumDoc("20100066603");
        var n=new NotaCredito();n.setSerie("FC01");n.setNumero("00000001");n.setComprobanteReferencia(c);n.setMotivo("Anulación solicitada por el cliente");n.setTotal(new BigDecimal("1180.00"));n.setEstadoSunat(Comprobante.EstadoSunat.ACEPTADO);n.setAmbienteEmision(Comprobante.AmbienteEmision.BETA);
        byte[] bytes=new NotaCreditoPdfService(null,new EmpresaProperties("JD REFRIGERACIÓN - DEMO","20000000001","Lima",null)).generar(n);
        try(var pdf=new PdfReader(bytes)){assertThat(pdf.getNumberOfPages()).isEqualTo(1);assertThat(new PdfTextExtractor(pdf).getTextFromPage(1)).contains("FC01-00000001","F001-00000001","Anulación solicitada","1180.00","BETA","SIN VALIDEZ TRIBUTARIA","Cliente de la factura original");}
        java.nio.file.Files.write(java.nio.file.Path.of("target/nota_credito_demo.pdf"),bytes);
    }
}
