package com.bomerp.proformas;

import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.catalogo.producto.repository.ProductoRepository;
import com.bomerp.notificaciones.notificacion.service.NotificadorCorreo;
import com.bomerp.notificaciones.notificacion.service.NotificadorCorreo.CorreoNoEnviadoException;
import com.bomerp.notificaciones.notificacion.service.NotificadorCorreo.MensajeCorreo;
import com.bomerp.notificaciones.notificacion.service.NotificadorWhatsApp;
import com.bomerp.proformas.pdf.service.GeneradorPdfService;
import com.bomerp.proformas.proforma.entity.Proforma;
import com.bomerp.proformas.proforma.service.ProformaService;
import com.bomerp.proformas.proformarequest.entity.ProformaRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Envío de proformas por correo contra Oracle (el servidor de correo es simulado: no sale ningún correo).
 * Revisa que el correo se envíe fuera de toda transacción, que un fallo del SMTP no provoque un segundo correo,
 * que un fallo del PDF se distinga (ENVIADO_SIN_PDF) y que la vista previa entregue el mismo PDF.
 *
 * No es @Transactional: enviar() abre sus propias transacciones cortas y necesita ver los datos confirmados.
 * Lo que crea (proformas, el producto, el cliente) se borra al terminar.
 */
@Tag("oracle")
@SpringBootTest(properties = "app.facturacion.reintento-inicial-ms=86400000")
class ProformaEnvioIntegracionTest {

    @MockitoBean private NotificadorCorreo correo;
    @MockitoBean private NotificadorWhatsApp whatsApp;
    @MockitoSpyBean private GeneradorPdfService generadorPdf;

    @Autowired private ProformaService proformaService;
    @Autowired private ProductoRepository productos;
    @Autowired private JdbcTemplate jdbc;

    private Long clienteId;
    private Long productoId;
    private final List<Long> proformas = new ArrayList<>();

    @BeforeEach
    void preparar() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("lucy", null, "ROLE_ADMIN"));
        // Cliente propio con un dominio reservado (example.com): no depende de los datos de la base
        jdbc.update("INSERT INTO clientes (tipo, nombreorazon_social, ruc, direccion, correo) "
                + "VALUES ('EMPRESA', 'CLIENTE DE PRUEBA ENVIO PROFORMA', '20999999917', 'LIMA', 'compras@example.com')");
        clienteId = jdbc.queryForObject("SELECT id FROM clientes WHERE ruc = '20999999917'", Long.class);
        Producto p = new Producto();
        p.setNombre("Servicio de prueba (envío de proforma)");
        p.setSector(Producto.Sector.SERVICIO);
        p.setCostoBase(new BigDecimal("100.00"));
        p.setMargenPorcentaje(new BigDecimal("10"));
        p.setMoneda(Producto.Moneda.SOLES);
        productoId = productos.save(p).getId();
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
        for (Long id : proformas) {
            jdbc.update("DELETE FROM items_proforma WHERE proforma_id = ?", id);
            jdbc.update("DELETE FROM proformas WHERE id = ?", id);
        }
        productos.deleteById(productoId);
        jdbc.update("DELETE FROM clientes WHERE id = ?", clienteId);
    }

    private Long nuevaProforma() {
        Long id = proformaService.crear(new ProformaRequest.CrearProformaDTO(clienteId, Proforma.Moneda.SOLES, 15,
                List.of(new ProformaRequest.ItemDTO(productoId, 2, BigDecimal.ZERO, null, null)), null)).id();
        proformas.add(id);
        return id;
    }

    private boolean enviadoCorreo(Long id) {
        return jdbc.queryForObject("SELECT enviado_correo FROM proformas WHERE id = ?", Integer.class, id) == 1;
    }

    @Test
    void elCorreoSaleConElPdfYFueraDeLaTransaccion() {
        Long id = nuevaProforma();
        AtomicBoolean conTransaccion = new AtomicBoolean(true);
        when(correo.enviarConAdjuntos(any())).thenAnswer(inv -> {
            conTransaccion.set(TransactionSynchronizationManager.isActualTransactionActive());
            return "<id@prueba>";
        });

        ProformaService.ResultadoEnvio r = proformaService.enviar(id, false, true);

        assertThat(r.correo()).isEqualTo("ENVIADO");
        assertThat(conTransaccion.get()).as("el SMTP no debe correr dentro de una transacción").isFalse();
        ArgumentCaptor<MensajeCorreo> mensaje = ArgumentCaptor.forClass(MensajeCorreo.class);
        verify(correo).enviarConAdjuntos(mensaje.capture());
        NotificadorCorreo.Adjunto pdf = mensaje.getValue().adjuntos().getFirst();
        assertThat(pdf.nombre()).isEqualTo("Proforma_" + id + ".pdf");
        assertThat(new String(pdf.contenido(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        verify(correo, never()).enviarCorreo(anyString(), anyString(), anyString());
        assertThat(enviadoCorreo(id)).isTrue();
    }

    @Test
    void siElSmtpFallaNoSeMandaUnSegundoCorreo() {
        Long id = nuevaProforma();
        when(correo.enviarConAdjuntos(any())).thenThrow(new CorreoNoEnviadoException("SMTP caído (prueba)"));

        ProformaService.ResultadoEnvio r = proformaService.enviar(id, false, true);

        assertThat(r.correo()).isEqualTo("FALLO");
        verify(correo, times(1)).enviarConAdjuntos(any());
        verify(correo, never()).enviarCorreo(anyString(), anyString(), anyString());
        assertThat(enviadoCorreo(id)).isFalse();
    }

    @Test
    void siFallaElPdfSeEnviaSinAdjuntoYSeDistingue() {
        Long id = nuevaProforma();
        doThrow(new IllegalStateException("PDF roto (prueba)")).when(generadorPdf).generarProformaPdf(any());
        when(correo.enviarCorreo(anyString(), anyString(), anyString())).thenReturn(true);

        ProformaService.ResultadoEnvio r = proformaService.enviar(id, false, true);

        assertThat(r.correo()).isEqualTo("ENVIADO_SIN_PDF");
        assertThat(r.mensaje()).contains("SIN el PDF");
        verify(correo, never()).enviarConAdjuntos(any());
        assertThat(enviadoCorreo(id)).isTrue();
    }

    @Test
    void laVistaPreviaEntregaElPdf() {
        Long id = nuevaProforma();
        ProformaService.PdfProforma pdf = proformaService.pdf(id);
        assertThat(pdf.nombreArchivo()).isEqualTo("Proforma_" + id + ".pdf");
        assertThat(new String(pdf.contenido(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        verifyNoInteractions(correo);
    }
}
