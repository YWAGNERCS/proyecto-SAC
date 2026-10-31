package com.bomerp.seguridad.permisos;

import com.bomerp.config.ReglaNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** RF-28: permisos delegables (roles PERM_* en Keycloak, simulado). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PermisosServiceTest {

    @Mock KeycloakAdminClient keycloak;
    @InjectMocks PermisosService service;

    private static JsonNode usuario(String id, String username) {
        return new ObjectMapper().readTree("{\"id\":\"" + id + "\",\"username\":\"" + username
                + "\",\"firstName\":\"" + username + "\",\"enabled\":true}");
    }

    @BeforeEach
    void usuarios() {
        when(keycloak.usuarios()).thenReturn(List.of(usuario("u1", "lisandro"), usuario("u2", "lucy")));
        when(keycloak.rolesDe("u1")).thenReturn(List.of("ADMIN"));
    }

    @Test
    void agregaLosQueFaltanYQuitaLosDemas() {
        when(keycloak.rolesDe("u2")).thenReturn(List.of("ASISTENTE", "PERM_CATALOGO", "offline_access"));

        service.actualizar("lucy", List.of("ANULAR_VENTAS"), "lisandro");

        verify(keycloak).asignarRoles("u2", List.of("PERM_ANULAR_VENTAS"));
        verify(keycloak).quitarRoles("u2", List.of("PERM_CATALOGO"));   // ASISTENTE y offline_access no se tocan
    }

    @Test
    void nadieCambiaSusPropiosPermisos() {
        assertThatThrownBy(() -> service.actualizar("Lisandro", List.of(), "lisandro"))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("propios");
        verify(keycloak, never()).asignarRoles(any(), any());
    }

    @Test
    void aUnAdministradorNoSeLeEditan() {
        when(keycloak.rolesDe("u1")).thenReturn(List.of("ADMIN"));

        assertThatThrownBy(() -> service.actualizar("lisandro", List.of(), "otro"))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("administrador");
    }

    @Test
    void unPermisoDesconocidoSeRechaza() {
        when(keycloak.rolesDe("u2")).thenReturn(List.of("ASISTENTE"));

        assertThatThrownBy(() -> service.actualizar("lucy", List.of("VOLAR"), "lisandro"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("VOLAR");
    }

    @Test
    void elListadoMuestraRolYPermisosEnPalabras() {
        when(keycloak.rolesDe("u2")).thenReturn(List.of("ASISTENTE", "PERM_VER_RENTABILIDAD"));

        PermisosService.PermisosDTO d = service.listar();

        assertThat(d.catalogo()).hasSize(Permiso.values().length);
        assertThat(d.usuarios().get(0).rol()).isEqualTo("ADMIN");          // el administrador primero
        assertThat(d.usuarios().get(1).permisos()).containsExactly("VER_RENTABILIDAD");
    }

    @Test
    void elRolDeKeycloakSeTraduceAlPermiso() {
        assertThat(Permiso.deRol("PERM_AJUSTAR_INVENTARIO")).contains(Permiso.AJUSTAR_INVENTARIO);
        assertThat(Permiso.deRol("ADMIN")).isEmpty();
        assertThat(Permiso.CATALOGO.rol()).isEqualTo("PERM_CATALOGO");
    }
}
