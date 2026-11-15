package com.bomerp.proformas.proforma.service;

import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.clientes.general.integration.Cliente;
import com.bomerp.config.empresa.EmpresaProperties;
import com.bomerp.proformas.itemproforma.entity.ItemProforma;
import com.bomerp.proformas.proforma.entity.Proforma;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProformaHtmlEmailTest {

    @Test
    void generaBotonYEnlaceWhatsAppCorrectamente() {
        EmpresaProperties empresa = new EmpresaProperties("JD REFRIGERACIÓN S.A.C.", "20521632268", "Lima", null,
                "912345678", "ventas@jd.pe", "jd.pe");
        ProformaService servicio = new ProformaService(null, null, null, null, null, null, null, null, null, null, empresa);

        Cliente cliente = new Cliente();
        cliente.setNombreORazonSocial("Clinica Javier Prado S.A.");

        Proforma proforma = new Proforma();
        proforma.setId(105L);
        proforma.setCliente(cliente);
        proforma.setFechaEmision(LocalDate.of(2026, 11, 15));
        proforma.setMoneda(Proforma.Moneda.SOLES);

        Producto producto = new Producto();
        producto.setNombre("Compresor Copeland 5HP");

        ItemProforma item = new ItemProforma();
        item.setProforma(proforma);
        item.setProducto(producto);
        item.setCantidad(2);
        item.setPrecioUnitario(new BigDecimal("1500.00"));
        item.setDescuentoPorcentaje(BigDecimal.ZERO);
        proforma.setItems(List.of(item));

        String html = servicio.generarHtmlProforma(proforma);

        assertThat(html).contains("Coordinar aprobación por WhatsApp &gt;");
        assertThat(html).contains("cid:whatsapp");
        assertThat(html).contains("https://wa.me/51912345678");
        assertThat(html).contains("background-color: #25D366");
        assertThat(html).contains("proforma%20N%C2%B0%20105");
        assertThat(html).contains("Clinica%20Javier%20Prado");
    }

    @Test
    void enlaceWhatsAppSinTelefonoConfiguradoUsaFallback() {
        EmpresaProperties empresa = new EmpresaProperties("JD REFRIGERACIÓN S.A.C.", "20521632268", "Lima", null,
                null, null, null);
        ProformaService servicio = new ProformaService(null, null, null, null, null, null, null, null, null, null, empresa);

        Proforma proforma = new Proforma();
        proforma.setId(200L);
        proforma.setMoneda(Proforma.Moneda.DOLARES);

        String enlace = servicio.construirEnlaceWhatsApp(proforma);

        assertThat(enlace).startsWith("https://wa.me/51912345678?text=");
        assertThat(enlace).contains("200");
    }
}
