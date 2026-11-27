package com.bomerp.facturacion;

import com.bomerp.facturacion.pdf.service.GeneradorPdfFacturacionService;
import com.bomerp.facturacion.comprobante.entity.Comprobante;
import com.bomerp.config.empresa.EmpresaProperties;
import com.bomerp.ventas.venta.entity.Venta;
import com.bomerp.ventas.detalleventa.entity.DetalleVenta;
import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.clientes.general.integration.Cliente;
import com.lowagie.text.pdf.*;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

class PdfEditorialPresentacionTest {
    private Comprobante boleta(int lineas) {
        var cliente=new Cliente();cliente.setNombreORazonSocial("Cliente de demostración");cliente.setDni("12345678");cliente.setDireccion("Dirección de prueba, Lima");
        var v=new Venta();v.setCliente(cliente);v.setMoneda(Venta.Moneda.SOLES);v.setTipoPago(Venta.TipoPago.CONTADO);
        for(int i=1;i<=lineas;i++) {var p=new Producto();p.setNombre("Servicio de prueba "+i+" - mantenimiento preventivo de equipos de refrigeración");v.agregarDetalle(new DetalleVenta(p,1,new BigDecimal("100")));}
        var c=new Comprobante();c.setVenta(v);c.setTipo(Comprobante.TipoComprobante.BOLETA);c.setSerie("B001");c.setNumero("00000001");c.setFechaEmision(LocalDate.of(2026,11,26));c.registrarAmbiente(Comprobante.AmbienteEmision.BETA);return c;
    }
    @Test void boletaYDocumentoExtensoConservanDetallesYCabeceras() throws Exception {
        var servicio=new GeneradorPdfFacturacionService(new EmpresaProperties("JD REFRIGERACIÓN - DEMO","20000000001","Dirección de demostración, Lima",null));
        for(int filas:new int[]{2,65}) {
            byte[] bytes=servicio.generarFacturaPdfBytes(boleta(filas));
            try(var r=new PdfReader(bytes)) {
                var texto=new StringBuilder();var extractor=new PdfTextExtractor(r);
                for(int i=1;i<=r.getNumberOfPages();i++) {String pagina=extractor.getTextFromPage(i);texto.append(pagina);assertThat(pagina).contains("Página "+i);}
                assertThat(texto.toString()).contains("BOLETA ELECTRÓNICA","B001-00000001","SIN VALIDEZ TRIBUTARIA","Servicio de prueba "+filas);
                if(filas==2) assertThat(r.getNumberOfPages()).isEqualTo(1);else assertThat(r.getNumberOfPages()).isGreaterThan(1);
            }
            Files.write(Path.of("target/boleta_editorial_"+filas+".pdf"),bytes);
        }
    }
}
