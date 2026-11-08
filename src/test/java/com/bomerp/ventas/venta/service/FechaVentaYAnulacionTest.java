package com.bomerp.ventas.venta.service;

import com.bomerp.catalogo.producto.service.ProductoService;
import com.bomerp.clientes.cliente.service.ClienteService;
import com.bomerp.config.tipocambio.TipoCambioService;
import com.bomerp.proformas.proforma.service.ProformaService;
import com.bomerp.seguridad.acceso.AccesoUsuario;
import com.bomerp.seguridad.descuento.TopeDescuentoService;
import com.bomerp.ventas.tributos.DeduccionTributariaService;
import com.bomerp.ventas.venta.repository.VentaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Auditoría M3 (fecha real de la venta, hasta 30 días atrás y con permiso) y M4 (motivo obligatorio al anular).
 * Sin Oracle: el repositorio y los demás servicios son mocks.
 */
class FechaVentaYAnulacionTest {

    private AccesoUsuario acceso;
    private VentaRepository ventas;
    private VentaService servicio;
    private final LocalDate hoy = LocalDate.now();

    @BeforeEach
    void preparar() {
        acceso = mock(AccesoUsuario.class);
        ventas = mock(VentaRepository.class);
        servicio = new VentaService(ventas, mock(ProformaService.class), mock(ClienteService.class),
                mock(ProductoService.class), mock(TipoCambioService.class), mock(TopeDescuentoService.class),
                mock(DeduccionTributariaService.class), mock(ApplicationEventPublisher.class), acceso);
    }

    @Test
    void sinFechaOConLaDeHoyEsHoyYNoPideNingunPermiso() {
        assertThat(servicio.resolverFechaVenta(null)).isEqualTo(hoy);
        assertThat(servicio.resolverFechaVenta(hoy)).isEqualTo(hoy);
        verifyNoInteractions(acceso);
    }

    @Test
    void conPermisoSeAceptaHastaTreintaDiasAtras() {
        when(acceso.puedeRegistrarVentasConFechaPasada()).thenReturn(true);
        assertThat(servicio.resolverFechaVenta(hoy.minusDays(1))).isEqualTo(hoy.minusDays(1));
        assertThat(servicio.resolverFechaVenta(hoy.minusDays(30))).isEqualTo(hoy.minusDays(30));
        assertThatThrownBy(() -> servicio.resolverFechaVenta(hoy.minusDays(31)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("hasta 30 días atrás");
    }

    @Test
    void nuncaUnaFechaFutura() {
        when(acceso.puedeRegistrarVentasConFechaPasada()).thenReturn(true);
        assertThatThrownBy(() -> servicio.resolverFechaVenta(hoy.plusDays(1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no puede ser futura");
    }

    @Test
    void sinPermisoUnaFechaPasadaResponde403() {
        when(acceso.puedeRegistrarVentasConFechaPasada()).thenReturn(false);
        assertThatThrownBy(() -> servicio.resolverFechaVenta(hoy.minusDays(5)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No tiene permiso para registrar ventas con fecha pasada")
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(403));
    }

    @Test
    void anularSinMotivoOConMenosDeCincoCaracteresNoTocaLaVenta() {
        assertThatThrownBy(() -> servicio.anular(7L, null, "lisandro"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("motivo de la anulación");
        assertThatThrownBy(() -> servicio.anular(7L, "   abc  ", "lisandro"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("al menos 5 caracteres");
        assertThatThrownBy(() -> servicio.anular(7L, "x".repeat(301), "lisandro"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("hasta 300 caracteres");
        verify(ventas, never()).bloquear(any());
    }
}
