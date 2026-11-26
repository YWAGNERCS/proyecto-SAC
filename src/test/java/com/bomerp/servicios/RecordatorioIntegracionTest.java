package com.bomerp.servicios;

import com.bomerp.servicios.recordatorio.*;
import com.bomerp.servicios.orden.entity.OrdenTrabajo;
import com.bomerp.servicios.orden.repository.OrdenTrabajoRepository;
import com.bomerp.servicios.orden.service.OrdenTrabajoService;
import com.bomerp.servicios.orden.dto.OrdenDTO;
import com.bomerp.clientes.general.integration.Cliente;
import com.bomerp.clientes.cliente.repository.ClienteRepository;
import com.bomerp.notificaciones.notificacion.service.NotificadorCorreo;
import com.bomerp.seguridad.permisos.KeycloakAdminClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:recordatorios;MODE=Oracle;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.default_schema=","spring.sql.init.mode=never",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:1/keys",
        "app.facturacion.modo=simulado","spring.mail.username=","app.notificaciones.correo-oficina=oficina@example.com","app.recordatorios.cron=-"})
class RecordatorioIntegracionTest {
    @Autowired RecordatorioService servicio;@Autowired RecordatorioRepository repo;@Autowired OrdenTrabajoRepository ordenes;
    @Autowired ClienteRepository clientes;@Autowired OrdenTrabajoService trabajos;
    @MockitoBean NotificadorCorreo correo;@MockitoBean KeycloakAdminClient keycloak;
    Long id;
    @BeforeEach void preparar(){
        var c=new Cliente();c.setTipo(Cliente.TipoCliente.EMPRESA);c.setNombreORazonSocial("Cliente <demo>");c.setCorreo("cliente@example.com");clientes.save(c);
        var o=new OrdenTrabajo();o.setCliente(c);o.setTipo(OrdenTrabajo.Tipo.MANTENIMIENTO);o.setTecnico("Técnico");o.setDescripcion("Mantenimiento de cinco equipos");o.setFechaProgramada(LocalDate.now().minusMonths(3));id=ordenes.save(o).getId();
        when(correo.enviarConAdjuntos(any())).thenReturn("id-prueba");
    }
    void cerrar(){trabajos.cerrar(id,new OrdenDTO.Cierre(LocalDate.now().minusMonths(3),"Cinco equipos revisados","Cliente",null,List.of(),List.of(),new RecordatorioService.Config(true,3,3)),"prueba");}
    @Test void cierreCalculaLaFechaYEnviaAClienteYOficinaUnaSolaVez(){
        cerrar();var r=servicio.obtener(id);assertThat(r.fechaSiguiente()).isEqualTo(LocalDate.now().minusMonths(3).plusMonths(3));assertThat(r.fechaAviso()).isEqualTo(r.fechaSiguiente().minusDays(3));
        servicio.procesar(LocalDate.now());servicio.procesar(LocalDate.now());
        verify(correo,times(2)).enviarConAdjuntos(any());assertThat(servicio.obtener(id).estadoCliente()).isEqualTo("ENVIADO");assertThat(servicio.obtener(id).estadoOficina()).isEqualTo("ENVIADO");
        servicio.configurar(id,new RecordatorioService.Config(true,3,3));servicio.procesar(LocalDate.now());verify(correo,times(2)).enviarConAdjuntos(any());
    }
    @Test void fallaSoloUnDestinatarioYNoRepiteElQueYaRecibio(){
        cerrar();when(correo.enviarConAdjuntos(argThat(m->m!=null && m.destinatario().equals("cliente@example.com")))).thenThrow(new NotificadorCorreo.CorreoNoEnviadoException("Prueba: correo no aceptado"));
        servicio.procesar(LocalDate.now());servicio.procesar(LocalDate.now());verify(correo,times(2)).enviarConAdjuntos(any());
        assertThat(servicio.obtener(id).estadoCliente()).isEqualTo("FALLIDO");assertThat(servicio.obtener(id).estadoOficina()).isEqualTo("ENVIADO");
        assertThatThrownBy(()->servicio.reintentar(id,false)).hasMessageContaining("Confirme");servicio.reintentar(id,true);
        when(correo.enviarConAdjuntos(any())).thenReturn("prueba");servicio.procesar(LocalDate.now());verify(correo,times(3)).enviarConAdjuntos(any());
    }
    @Test void caidaDuranteEnvioQuedaInciertaSinReenvioAutomatico(){
        cerrar();var r=repo.findByOrden_Id(id).orElseThrow();r.setEstadoCliente("ENVIANDO");r.setIntentoCliente(LocalDateTime.now().minusHours(1));repo.save(r);
        servicio.procesar(LocalDate.now());servicio.procesar(LocalDate.now());assertThat(servicio.obtener(id).estadoCliente()).isEqualTo("INCIERTO");verify(correo,times(1)).enviarConAdjuntos(any());
    }
    @Test void frecuenciaSeisMesesSePuedeDesactivarSinCrearOtraVisita(){
        cerrar();servicio.configurar(id,new RecordatorioService.Config(true,6,2));assertThat(servicio.obtener(id).fechaSiguiente()).isEqualTo(LocalDate.now().minusMonths(3).plusMonths(6));
        servicio.configurar(id,new RecordatorioService.Config(false,6,2));servicio.procesar(LocalDate.now());verifyNoInteractions(correo);
    }
    @Test void dosProcesosNoDuplicanLosCorreos() throws Exception {
        cerrar();var inicio=new java.util.concurrent.CyclicBarrier(2);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Void> tarea=()->{inicio.await();servicio.procesar(LocalDate.now());return null;};
            var a=pool.submit(tarea);var b=pool.submit(tarea);a.get(15,java.util.concurrent.TimeUnit.SECONDS);b.get(15,java.util.concurrent.TimeUnit.SECONDS);
        }
        verify(correo,times(2)).enviarConAdjuntos(any());
    }
    @Test void cambiarUnCorreoNoRepiteElOtroDestinatario() {
        cerrar();servicio.procesar(LocalDate.now());
        var o=ordenes.findById(id).orElseThrow();var c=clientes.findById(o.getCliente().getId()).orElseThrow();c.setCorreo("nuevo@example.com");clientes.save(c);
        servicio.configurar(id,new RecordatorioService.Config(true,3,3));servicio.procesar(LocalDate.now());
        verify(correo,times(3)).enviarConAdjuntos(any());
        verify(correo,times(1)).enviarConAdjuntos(argThat(m->m!=null&&m.destinatario().equals("oficina@example.com")));
    }
    @Test void siFaltaCorreoElCierreCompletoSeRevierte() {
        var o=ordenes.findById(id).orElseThrow();var c=clientes.findById(o.getCliente().getId()).orElseThrow();c.setCorreo(null);clientes.save(c);
        assertThatThrownBy(this::cerrar).hasMessageContaining("correo válido");
        assertThat(ordenes.findById(id).orElseThrow().getEstado()).isEqualTo(OrdenTrabajo.Estado.PROGRAMADA);
        assertThat(repo.findByOrden_Id(id)).isEmpty();verifyNoInteractions(correo);
    }
    @AfterEach void limpiar(){repo.deleteAll();ordenes.deleteAll();clientes.deleteAll();}
}
