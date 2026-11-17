package com.bomerp.proformas;

import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.clientes.general.integration.Cliente;
import com.bomerp.config.empresa.EmpresaProperties;
import com.bomerp.proformas.itemproforma.entity.ItemProforma;
import com.bomerp.proformas.pdf.service.GeneradorPdfService;
import com.bomerp.proformas.proforma.dto.ProformaResponseDTO;
import com.bomerp.proformas.proforma.entity.Proforma;
import com.bomerp.proformas.proformarequest.entity.ProformaRequest;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProformaCondicionesPdfTest {
    private Proforma proforma() {
        Cliente cliente = new Cliente();
        cliente.setNombreORazonSocial("Cliente de prueba");
        cliente.setRuc("20123456789");
        Proforma proforma = new Proforma();
        proforma.setId(1L);
        proforma.setCliente(cliente);
        proforma.setMoneda(Proforma.Moneda.SOLES);
        proforma.setFechaVencimiento(proforma.getFechaEmision().plusDays(15));
        return proforma;
    }

    private String texto(byte[] pdf) throws Exception {
        try (PdfReader reader = new PdfReader(pdf)) {
            StringBuilder texto = new StringBuilder();
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            for (int pagina = 1; pagina <= reader.getNumberOfPages(); pagina++) {
                texto.append(extractor.getTextFromPage(pagina));
            }
            return texto.toString();
        }
    }

    @Test
    void conservaAcuerdoYSeparaCuentaGeneralDeDetracciones() throws Exception {
        Proforma proforma = proforma();
        proforma.getCliente().setRuc(null);
        proforma.getCliente().setDni("12345678");
        Producto servicio = new Producto();
        servicio.setNombre("Refrigeración");
        proforma.agregarItem(new ItemProforma(servicio, 1, new BigDecimal("920.00"), BigDecimal.ZERO));
        proforma.setCondicionesPago("60 % de adelanto y 40 % a la entrega.\nSaldo a 60 días desde la factura.");
        EmpresaProperties empresa = new EmpresaProperties("JD", "20521632268", "Lima", "DETRACCIONES-PRUEBA",
                null, null, null, "BCP-PRUEBA", null, "NACION-PRUEBA");
        byte[] pdf = new GeneradorPdfService(empresa).generarProformaPdf(proforma);
        assertThat(texto(pdf)).contains("60 % de adelanto", "Saldo a 60 días", "BCP-PRUEBA",
                "cuenta de detracciones: DETRACCIONES-PRUEBA", "cuenta: NACION-PRUEBA",
                "DNI: 12345678", "S/ 920.00", "S/ 1085.60").doesNotContain("CCI:", "RUC: null", "SOLES");
        assertThat(ProformaResponseDTO.desdeEntidad(proforma).condicionesPago()).isEqualTo(proforma.getCondicionesPago());
        // Muestra de la salida real para revisar visualmente el PDF.
        java.nio.file.Files.write(java.nio.file.Path.of("target/proforma-condiciones-prueba.pdf"), pdf);
    }

    @Test
    void proformaAnteriorSinCuentasNoImprimeDatosFicticios() throws Exception {
        String texto = texto(new GeneradorPdfService(new EmpresaProperties("JD", "123", "Lima", null))
                .generarProformaPdf(proforma()));
        assertThat(texto).doesNotContain("193-12345678", "00-012-345678", "Cuentas bancarias", "Condiciones de pago", "null");
    }

    @Test
    void rechazaCondicionesDemasiadoLargasYAdmiteSolicitudesAnteriores() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var items = List.of(new ProformaRequest.ItemDTO(1L, 1, null));
            var anterior = new ProformaRequest.CrearProformaDTO(1L, Proforma.Moneda.SOLES, 15, items, null);
            assertThat(validator.validate(anterior)).isEmpty();
            var excesiva = new ProformaRequest.CrearProformaDTO(1L, Proforma.Moneda.SOLES, 15, items, null, "a".repeat(1001));
            assertThat(validator.validate(excesiva)).anyMatch(v -> v.getPropertyPath().toString().equals("condicionesPago"));
        }
    }
}
