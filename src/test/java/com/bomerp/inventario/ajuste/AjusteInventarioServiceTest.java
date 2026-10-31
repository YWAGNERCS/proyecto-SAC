package com.bomerp.inventario.ajuste;

import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.catalogo.producto.service.ProductoService;
import com.bomerp.config.ReglaNegocioException;
import com.bomerp.inventario.ajuste.entity.AjusteInventario;
import com.bomerp.inventario.ajuste.repository.AjusteInventarioRepository;
import com.bomerp.inventario.ajuste.service.AjusteInventarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** RF-31: ajustes de inventario (conteo físico y movimientos con motivo). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AjusteInventarioServiceTest {

    @Mock AjusteInventarioRepository repository;
    @Mock ProductoService productoService;
    @InjectMocks AjusteInventarioService service;

    private Producto material;

    @BeforeEach
    void materialConSieteUnidades() {
        material = new Producto();
        material.setId(18L);
        material.setNombre("Nitrógeno seco");
        material.setSector(Producto.Sector.MATERIAL_SERVICIO);
        material.setStockActual(7);
        when(productoService.obtenerPorId(18L)).thenReturn(material);
        when(repository.save(any(AjusteInventario.class))).thenAnswer(i -> {
            AjusteInventario a = i.getArgument(0);
            a.setId(1L);
            return a;
        });
    }

    private AjusteInventarioService.AjusteRequest conteo(int contado) {
        return new AjusteInventarioService.AjusteRequest(18L, AjusteInventario.Motivo.CONTEO_FISICO, null, contado, null, null, null);
    }

    private AjusteInventarioService.AjusteRequest movimiento(AjusteInventario.Tipo tipo, int cantidad, String observacion) {
        return new AjusteInventarioService.AjusteRequest(18L, AjusteInventario.Motivo.MERMA, null, null, tipo, cantidad, observacion);
    }

    @Test
    void elConteoFisicoRegistraLaDiferencia() {
        AjusteInventarioService.AjusteDTO sobra = service.registrar(conteo(9), "lisandro");
        assertThat(sobra.tipo()).isEqualTo("ENTRADA");
        assertThat(sobra.cantidad()).isEqualTo(2);
        assertThat(sobra.stockAnterior()).isEqualTo(7);
        assertThat(sobra.stockNuevo()).isEqualTo(9);
        assertThat(material.getStockActual()).isEqualTo(9);

        AjusteInventarioService.AjusteDTO falta = service.registrar(conteo(4), "lisandro");
        assertThat(falta.tipo()).isEqualTo("SALIDA");
        assertThat(falta.cantidad()).isEqualTo(5);
        assertThat(material.getStockActual()).isEqualTo(4);
    }

    @Test
    void unConteoIgualAlSistemaNoSeRegistra() {
        assertThatThrownBy(() -> service.registrar(conteo(7), "lisandro"))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("coincide");
        verify(repository, never()).save(any());
    }

    @Test
    void unaMermaRestaYGuardaQuienLaRegistro() {
        AjusteInventarioService.AjusteDTO a = service.registrar(movimiento(AjusteInventario.Tipo.SALIDA, 2, "cilindro con fuga"), "lisandro");

        assertThat(a.stockNuevo()).isEqualTo(5);
        assertThat(a.registradoPor()).isEqualTo("lisandro");
        assertThat(a.motivoNombre()).isEqualTo("Merma");
        verify(productoService).guardar(material);
    }

    @Test
    void unMovimientoSinObservacionSeRechaza() {
        assertThatThrownBy(() -> service.registrar(movimiento(AjusteInventario.Tipo.SALIDA, 2, "  "), "lisandro"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("observación");
    }

    @Test
    void unaSalidaNoDejaElStockNegativo() {
        assertThatThrownBy(() -> service.registrar(movimiento(AjusteInventario.Tipo.SALIDA, 8, "robo"), "lisandro"))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("negativo");
        assertThat(material.getStockActual()).isEqualTo(7);
    }

    @Test
    void losEquiposNoSeAjustan() {
        material.setSector(Producto.Sector.CLIMATIZACION);

        assertThatThrownBy(() -> service.registrar(movimiento(AjusteInventario.Tipo.SALIDA, 1, "dañado"), "lisandro"))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no controla stock");
    }

    @Test
    void elConteoNoSeHaceComoMovimiento() {
        var raro = new AjusteInventarioService.AjusteRequest(18L, AjusteInventario.Motivo.CONTEO_FISICO, null, null,
                AjusteInventario.Tipo.ENTRADA, 1, "conteo");
        assertThatThrownBy(() -> service.registrar(raro, "lisandro")).hasMessageContaining("stockContado");
    }
}
