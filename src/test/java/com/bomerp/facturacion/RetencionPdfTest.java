package com.bomerp.facturacion;

import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.clientes.general.integration.Cliente;
import com.bomerp.config.empresa.EmpresaProperties;
import com.bomerp.facturacion.comprobante.entity.Comprobante;
import com.bomerp.facturacion.pdf.service.GeneradorPdfFacturacionService;
import com.bomerp.ventas.detalleventa.entity.DetalleVenta;
import com.bomerp.ventas.venta.entity.Venta;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

class RetencionPdfTest {
    @Test
    void presentaLaRetencionComoPrevisionYExplicaElPagoParcial() throws Exception {
        Cliente cliente = new Cliente(); cliente.setNombreORazonSocial("Cliente de prueba");
        Producto producto = new Producto(); producto.setNombre("Equipo de prueba");
        Venta venta = new Venta(); venta.setCliente(cliente); venta.setMoneda(Venta.Moneda.SOLES);
        venta.agregarDetalle(new DetalleVenta(producto, 1, new BigDecimal("1000")));
        venta.setTipoDeduccion(Venta.TipoDeduccion.RETENCION);
        venta.setPorcentajeDeduccion(new BigDecimal("3")); venta.setMontoDeduccion(new BigDecimal("35.40"));
        Comprobante c = new Comprobante(); c.setVenta(venta); c.setTipo(Comprobante.TipoComprobante.FACTURA);
        c.setSerie("F001"); c.setNumero("00000001"); c.setFechaEmision(LocalDate.now());
        byte[] pdf = new GeneradorPdfFacturacionService(new EmpresaProperties("JD", "20521632268", "Lima", null))
                .generarFacturaPdfBytes(c);
        try (PdfReader reader = new PdfReader(pdf)) {
            StringBuilder texto = new StringBuilder(); var extractor = new PdfTextExtractor(reader);
            for (int i = 1; i <= reader.getNumberOfPages(); i++) texto.append(extractor.getTextFromPage(i));
            assertThat(texto.toString().replaceAll("\\s+", " "))
                    .contains("RETENCIÓN PREVISTA", "NETO ESTIMADO", "Se calcula sobre cada pago", "35.40");
        }
    }
}
