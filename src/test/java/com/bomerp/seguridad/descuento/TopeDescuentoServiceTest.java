package com.bomerp.seguridad.descuento;

import com.bomerp.seguridad.descuento.datos.TopesDescuento;
import com.bomerp.seguridad.descuento.datos.TopesDescuentoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Topes de descuento editables en Configuración (solo Lisandro): sin fila valen los de application.properties. */
class TopeDescuentoServiceTest {

    private TopesDescuentoRepository repository;
    private TopeDescuentoService servicio;
    private TopesDescuento guardada;

    @BeforeEach
    void preparar() {
        repository = mock(TopesDescuentoRepository.class);
        when(repository.findById(TopesDescuento.ID_UNICO)).thenAnswer(i -> Optional.ofNullable(guardada));
        when(repository.save(any())).thenAnswer(i -> guardada = i.getArgument(0));
        servicio = new TopeDescuentoService(new BigDecimal("30"), new BigDecimal("10"), repository);
    }

    @AfterEach
    void cerrarSesion() {
        SecurityContextHolder.clearContext();
    }

    private void comoUsuario(String... roles) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("x", null, roles));
    }

    @Test
    void sinCambiosValenLosIniciales() {
        assertThat(servicio.topes().topeAdmin()).isEqualByComparingTo("30");
        comoUsuario("ROLE_ASISTENTE");
        assertThat(servicio.topeActual()).isEqualByComparingTo("10");
        comoUsuario("ROLE_ASISTENTE", "ROLE_PERM_DESCUENTO_AMPLIADO");
        assertThat(servicio.topeActual()).isEqualByComparingTo("30");
    }

    @Test
    void lisandroLosCambiaYRigenEnSeguida() {
        var topes = servicio.cambiar(new BigDecimal("25"), new BigDecimal("8"), "lisandro");
        assertThat(topes.actualizadoPor()).isEqualTo("lisandro");
        assertThat(topes.fechaActualizacion()).isNotNull();

        comoUsuario("ROLE_ASISTENTE");
        assertThat(servicio.topeActual()).isEqualByComparingTo("8");
        assertThatThrownBy(() -> servicio.validar(new BigDecimal("9")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("8 %");
        comoUsuario("ROLE_ADMIN");
        assertThat(servicio.validar(new BigDecimal("25"))).isEqualByComparingTo("25");
    }

    @Test
    void rechazaTopesFueraDeRangoOAsistenteMayorQueAdministrador() {
        assertThatThrownBy(() -> servicio.cambiar(new BigDecimal("101"), BigDecimal.ONE, "lisandro"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.cambiar(BigDecimal.TEN, new BigDecimal("-1"), "lisandro"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.cambiar(BigDecimal.TEN, new BigDecimal("12"), "lisandro"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no puede ser mayor");
        assertThatThrownBy(() -> servicio.cambiar(null, BigDecimal.ONE, "lisandro"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }
}
