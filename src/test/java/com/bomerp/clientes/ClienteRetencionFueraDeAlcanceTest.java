package com.bomerp.clientes;

import com.bomerp.clientes.cliente.repository.ClienteRepository;
import com.bomerp.clientes.cliente.service.ClienteService;
import com.bomerp.clientes.consultaruc.integration.ConsultaRucClient;
import com.bomerp.clientes.general.integration.Cliente;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ClienteRetencionFueraDeAlcanceTest {
    private final ClienteRepository repositorio=mock(ClienteRepository.class);
    private final ClienteService servicio=new ClienteService(repositorio,mock(ConsultaRucClient.class));

    private Cliente cliente() {
        Cliente c=new Cliente(); c.setTipo(Cliente.TipoCliente.EMPRESA);
        c.setRuc("20000000001"); c.setNombreORazonSocial("Cliente demo"); return c;
    }
    @Test
    void noPermiteActivarRetencionEnUnaFichaNuevaNiDesdeLaApi() {
        Cliente nuevo=cliente(); nuevo.setAgenteRetencion(true);
        when(repositorio.save(any())).thenAnswer(i->i.getArgument(0));
        assertThat(servicio.guardar(nuevo).isAgenteRetencion()).isFalse();
    }
    @Test
    void editarContactoNoBorraLaCondicionHistorica() {
        Cliente historico=cliente(); historico.setId(1L); historico.setAgenteRetencion(true);
        when(repositorio.findById(1L)).thenReturn(Optional.of(historico));
        when(repositorio.save(any())).thenAnswer(i->i.getArgument(0));
        Cliente cambios=cliente(); cambios.setTelefono("900000000");
        Cliente actualizado=servicio.actualizar(1L,cambios);
        assertThat(actualizado.isAgenteRetencion()).isTrue();
        assertThat(actualizado.getTelefono()).isEqualTo("900000000");
    }
}
