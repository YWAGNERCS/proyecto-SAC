package com.bomerp.servicios.orden;

import com.bomerp.catalogo.producto.service.ProductoService;
import com.bomerp.clientes.cliente.service.ClienteService;
import com.bomerp.clientes.general.integration.Cliente;
import com.bomerp.clientes.sede.service.SedeClienteService;
import com.bomerp.config.ReglaNegocioException;
import com.bomerp.postventa.equipo.service.EquipoInstaladoService;
import com.bomerp.postventa.mantenimiento.service.MantenimientoService;
import com.bomerp.servicios.orden.dto.OrdenDTO;
import com.bomerp.servicios.orden.entity.OrdenTrabajo;
import com.bomerp.servicios.orden.repository.OrdenTrabajoRepository;
import com.bomerp.servicios.orden.service.OrdenTrabajoService;
import com.bomerp.ventas.venta.service.VentaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** RF-33: un técnico no tiene dos visitas programadas que se crucen el mismo día. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgendaHorarioTest {

    @Mock OrdenTrabajoRepository ordenRepository;
    @Mock ClienteService clienteService;
    @Mock SedeClienteService sedeService;
    @Mock ProductoService productoService;
    @Mock VentaService ventaService;
    @Mock MantenimientoService mantenimientoService;
    @Mock EquipoInstaladoService equipoService;
    @InjectMocks OrdenTrabajoService service;

    private final LocalDate dia = LocalDate.now().plusDays(3);
    private Cliente cliente;

    @BeforeEach
    void preparar() {
        cliente = new Cliente();
        cliente.setId(5L);
        cliente.setNombreORazonSocial("Cliente de prueba");
        cliente.setDireccion("Av. Prueba 123");
        when(clienteService.obtenerPorId(5L)).thenReturn(cliente);
        when(ordenRepository.save(any(OrdenTrabajo.class))).thenAnswer(i -> {
            OrdenTrabajo o = i.getArgument(0);
            if (o.getId() == null) o.setId(99L);
            return o;
        });
    }

    /** Ya hay una visita de Luis de 09:00 a 11:00. */
    private void luisOcupadoDe9a11() {
        OrdenTrabajo existente = new OrdenTrabajo();
        existente.setId(41L);
        existente.setCliente(cliente);
        existente.setTecnico("Luis Pérez");
        existente.setFechaProgramada(dia);
        existente.setHoraInicio("09:00");
        existente.setDuracionMinutos(120);
        when(ordenRepository.programadasDelTecnico(anyString(), eq(dia))).thenReturn(List.of(existente));
    }

    private OrdenDTO.Request orden(String hora, Integer duracion) {
        return new OrdenDTO.Request(OrdenTrabajo.Tipo.VISITA_TECNICA, 5L, null, null, null, null, dia, hora, duracion,
                "Luis Pérez", null, "Revisión");
    }

    @Test
    void unaVisitaQueSeCruzaSeRechaza() {
        luisOcupadoDe9a11();

        assertThatThrownBy(() -> service.crear(orden("10:00", 60), "lucy"))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("#41").hasMessageContaining("09:00 a 11:00");
    }

    @Test
    void unaVisitaQueEmpiezaCuandoTerminaLaOtraSePermite() {
        luisOcupadoDe9a11();

        OrdenDTO.Response r = service.crear(orden("11:00", 60), "lucy");
        assertThat(r.horaInicio()).isEqualTo("11:00");
        assertThat(r.duracionMinutos()).isEqualTo(60);
    }

    @Test
    void unaVisitaQueTerminaJustoAntesSePermite() {
        luisOcupadoDe9a11();

        assertThat(service.crear(orden("08:00", 60), "lucy").id()).isEqualTo(99L);
    }

    @Test
    void sinHoraNoHayCruceQueDetectarYDuraDosHoras() {
        luisOcupadoDe9a11();

        OrdenDTO.Response r = service.crear(orden(null, null), "lucy");
        assertThat(r.horaInicio()).isNull();
        assertThat(r.duracionMinutos()).isEqualTo(OrdenTrabajo.DURACION_POR_DEFECTO);
    }

    @Test
    void unaVisitaNoPuedePasarDeMedianoche() {
        assertThatThrownBy(() -> service.crear(orden("23:00", 180), "lucy")).hasMessageContaining("medianoche");
    }

    @Test
    void laAgendaMuestraHastaSeisSemanas() {
        assertThatThrownBy(() -> service.agenda(dia, dia.plusDays(60))).hasMessageContaining("6 semanas");
        assertThatThrownBy(() -> service.agenda(dia, dia.minusDays(1))).hasMessageContaining("posterior");
    }
}
