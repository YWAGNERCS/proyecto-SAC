package com.bomerp.facturacion.sunat.integration;

import com.bomerp.config.empresa.EmpresaProperties;
import com.bomerp.facturacion.comprobante.entity.Comprobante;
import com.bomerp.facturacion.comprobante.repository.ComprobanteRepository;
import com.bomerp.facturacion.facturacion.service.EnvioSunatService;
import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * EnvioSunatService llama a SUNAT fuera de la transacción, con el comprobante ya separado de la sesión de Hibernate.
 * Esta prueba arma y firma el XML real (el mismo código que usa SunatDirectoClient) con un comprobante de la base
 * cargado así, para comprobar que no falta ninguna relación LAZY. No se conecta a SUNAT: solo arma y firma, con el
 * certificado de prueba de certificados/beta-prueba.p12 (su clave se lee de .env; sin ella la prueba se omite).
 */
@Tag("oracle")
@SpringBootTest(properties = "app.facturacion.reintento-inicial-ms=86400000")
class XmlFueraDeTransaccionTest {

    @Autowired private ComprobanteRepository comprobantes;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private EmpresaProperties empresa;

    private static CertificadoSunat certificadoDePrueba() throws Exception {
        Path env = Path.of(".env");
        assumeTrue(Files.exists(env) && Files.exists(Path.of("certificados/beta-prueba.p12")), "Falta .env o el certificado de prueba");
        String clave = Files.readAllLines(env).stream().filter(l -> l.startsWith("SUNAT_CERT_CLAVE="))
                .map(l -> l.substring(17).trim()).findFirst().orElse(null);
        assumeTrue(clave != null && !clave.isBlank(), "Falta SUNAT_CERT_CLAVE en .env");
        return CertificadoSunat.cargar("certificados/beta-prueba.p12", clave);
    }

    private Comprobante cargar(Long id, boolean precargar) {
        return new TransactionTemplate(txManager).execute(s -> {
            Comprobante c = comprobantes.findById(id).orElseThrow();
            if (precargar) EnvioSunatService.precargar(c);
            return c;
        });   // al salir, la sesión se cierra: lo que no se cargó ya no se puede leer
    }

    @Test
    void elXmlSeArmaYFirmaFueraDeLaTransaccionConLoPrecargado() throws Exception {
        CertificadoSunat certificado = certificadoDePrueba();
        Long id = jdbc.queryForObject("SELECT MAX(id) FROM comprobantes WHERE venta_id IS NOT NULL", Long.class);
        assumeTrue(id != null, "No hay comprobantes en la base");
        XmlComprobante xml = new XmlComprobante(empresa);

        // Control: sin la precarga, armarlo fuera de la transacción falla (así la prueba sí mide algo)
        Comprobante sinPrecarga = cargar(id, false);
        assertThatThrownBy(() -> xml.factura(sinPrecarga, certificado)).isInstanceOf(LazyInitializationException.class);

        Comprobante c = cargar(id, true);
        String firmado = new String(xml.factura(c, certificado), StandardCharsets.ISO_8859_1);
        assertThat(firmado).contains("<ds:Signature").contains(c.getSerie() + "-" + Integer.parseInt(c.getNumero()));
    }
}
