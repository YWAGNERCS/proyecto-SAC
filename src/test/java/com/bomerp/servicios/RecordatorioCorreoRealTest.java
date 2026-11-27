package com.bomerp.servicios;

import com.bomerp.clientes.cliente.repository.ClienteRepository;
import com.bomerp.clientes.general.integration.Cliente;
import com.bomerp.notificaciones.notificacion.service.*;
import com.bomerp.seguridad.permisos.KeycloakAdminClient;
import com.bomerp.servicios.orden.entity.OrdenTrabajo;
import com.bomerp.servicios.orden.repository.OrdenTrabajoRepository;
import com.bomerp.servicios.recordatorio.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.nio.file.*;
import java.time.*;
import java.util.Properties;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Solo se ejecuta expresamente: dos correos de prueba al buzón propio, nunca a clientes reales. */
@EnabledIfSystemProperty(named="sgejd.pruebaCorreo",matches="true")
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:correo_real_recordatorio;MODE=Oracle;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.default_schema=","spring.sql.init.mode=never",
    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:1/keys",
    "app.facturacion.modo=simulado","spring.mail.username=","app.recordatorios.cron=-"})
class RecordatorioCorreoRealTest {
    @Autowired RecordatorioService servicio;
    @Autowired RecordatorioRepository repo;
    @Autowired OrdenTrabajoRepository ordenes;
    @Autowired ClienteRepository clientes;
    @MockitoBean NotificadorCorreo correo;
    @MockitoBean KeycloakAdminClient keycloak;

    private static Properties configuracionLocal() {
        Properties config=new Properties();
        try {
            for(String archivo:new String[]{"application.properties","application-local.properties"})
                try(var reader=Files.newBufferedReader(Path.of("src/main/resources",archivo))){config.load(reader);}
        } catch(java.io.IOException e) {throw new java.io.UncheckedIOException(e);}
        return config;
    }
    @DynamicPropertySource static void destinoPropio(DynamicPropertyRegistry registry) {
        registry.add("app.notificaciones.correo-oficina",()->configuracionLocal().getProperty("spring.mail.username"));
    }
    @Test void envioRealControladoAClienteDePruebaYOficina() throws Exception {
        Properties config=configuracionLocal();
        String destino=config.getProperty("spring.mail.username");
        assertThat(destino).isNotBlank().contains("@");
        assertThat(config.getProperty("app.notificaciones.correo-oficina")).isEqualTo(destino);
        var sender=new JavaMailSenderImpl();
        sender.setHost(config.getProperty("spring.mail.host","smtp.gmail.com"));
        sender.setPort(Integer.parseInt(config.getProperty("spring.mail.port","587")));
        sender.setUsername(destino);sender.setPassword(config.getProperty("spring.mail.password"));
        sender.getJavaMailProperties().setProperty("mail.smtp.auth","true");
        sender.getJavaMailProperties().setProperty("mail.smtp.starttls.enable","true");
        sender.getJavaMailProperties().setProperty("mail.smtp.connectiontimeout","15000");
        sender.getJavaMailProperties().setProperty("mail.smtp.timeout","15000");
        sender.getJavaMailProperties().setProperty("mail.smtp.writetimeout","15000");
        var real=new NotificadorCorreoReal(sender);ReflectionTestUtils.setField(real,"remitente",destino);
        when(correo.enviarConAdjuntos(any())).thenAnswer(inv->real.enviarConAdjuntos(inv.getArgument(0)));
        var cliente=new Cliente();cliente.setTipo(Cliente.TipoCliente.EMPRESA);
        cliente.setNombreORazonSocial("[PRUEBA INTERNA] Validación de recordatorio SGE-JD");cliente.setCorreo(destino);clientes.save(cliente);
        var orden=new OrdenTrabajo();orden.setCliente(cliente);orden.setTipo(OrdenTrabajo.Tipo.MANTENIMIENTO);
        orden.setEstado(OrdenTrabajo.Estado.REALIZADA);orden.setTecnico("Técnico de demostración");
        orden.setDescripcion("Prueba de entrega de correos, sin visita real");
        orden.setFechaProgramada(LocalDate.now().minusMonths(3));orden.setFechaRealizada(LocalDate.now().minusMonths(3));
        ordenes.save(orden);
        try {
            servicio.configurar(orden.getId(),new RecordatorioService.Config(true,3,3));
            servicio.procesar(LocalDate.now());servicio.procesar(LocalDate.now());
            var estado=servicio.obtener(orden.getId());
            assertThat(estado.estadoCliente()).isEqualTo("ENVIADO");assertThat(estado.estadoOficina()).isEqualTo("ENVIADO");
            verify(correo,times(2)).enviarConAdjuntos(any());
            Files.writeString(Path.of("target/recordatorio-correo-real-resultado.txt"),
                    "PRUEBA INTERNA EN H2 AISLADO\nCliente: ENVIADO\nOficina: ENVIADO\nDos mensajes aceptados por SMTP. Segunda ejecución sin duplicados.\nNo acredita lectura del destinatario.\n");
        } finally {repo.deleteAll();ordenes.deleteAll();clientes.deleteAll();}
    }
}
