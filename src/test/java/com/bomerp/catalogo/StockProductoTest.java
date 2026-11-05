package com.bomerp.catalogo;

import com.bomerp.catalogo.categoria.service.CategoriaService;
import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.catalogo.producto.repository.ProductoRepository;
import com.bomerp.catalogo.producto.service.ProductoService;
import com.bomerp.config.StockInsuficienteException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

/** Movimientos de stock: bloqueo de filas, suma de líneas repetidas y todo-o-nada. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockProductoTest {

    @Mock ProductoRepository productoRepository;
    @Mock CategoriaService categoriaService;
    @Mock EntityManager entityManager;
    @InjectMocks ProductoService service;

    private Producto gas;
    private Producto cobre;
    private Producto split;

    private static Producto producto(long id, String nombre, Producto.Sector sector, int stock) {
        Producto p = new Producto();
        p.setId(id);
        p.setNombre(nombre);
        p.setSector(sector);
        p.setStockActual(stock);
        return p;
    }

    @BeforeEach
    void productos() {
        gas = producto(17, "Gas R410A", Producto.Sector.MATERIAL_SERVICIO, 10);
        cobre = producto(22, "Cobre 1/4", Producto.Sector.MATERIAL_SERVICIO, 5);
        split = producto(1, "Split 12 000", Producto.Sector.CLIMATIZACION, 0);
        when(entityManager.find(Producto.class, 17L)).thenReturn(gas);
        when(entityManager.find(Producto.class, 22L)).thenReturn(cobre);
        when(entityManager.find(Producto.class, 1L)).thenReturn(split);
    }

    @Test
    void elMismoProductoEnDosLineasSeSumaAntesDeValidar() {
        // El caso de la revisión: stock 10 y dos líneas de 6. La venta suma 12 y se rechaza entera.
        Map<Long, Integer> cantidades = new LinkedHashMap<>();
        cantidades.merge(17L, 6, Integer::sum);
        cantidades.merge(17L, 6, Integer::sum);

        assertThatThrownBy(() -> service.descontarStock(cantidades))
                .isInstanceOf(StockInsuficienteException.class).hasMessageContaining("disponible 10").hasMessageContaining("solicitado 12");
        assertThat(gas.getStockActual()).isEqualTo(10);
    }

    @Test
    void siUnoNoAlcanzaNoSeDescuentaNinguno() {
        assertThatThrownBy(() -> service.descontarStock(Map.of(17L, 4, 22L, 9)))
                .isInstanceOf(StockInsuficienteException.class).hasMessageContaining("Cobre");
        assertThat(gas.getStockActual()).isEqualTo(10);
        assertThat(cobre.getStockActual()).isEqualTo(5);
    }

    @Test
    void descuentaLosMaterialesEIgnoraLosEquipos() {
        service.descontarStock(Map.of(17L, 4, 1L, 2));

        assertThat(gas.getStockActual()).isEqualTo(6);
        assertThat(split.getStockActual()).isZero();
    }

    @Test
    void bloqueaLasFilasEnOrdenDeIdYReleeElStock() {
        service.descontarStock(Map.of(22L, 1, 17L, 1, 1L, 1));

        // Siempre 1, 17, 22: dos ventas con los mismos productos no se esperan mutuamente (sin interbloqueo)
        InOrder orden = inOrder(entityManager);
        orden.verify(entityManager).refresh(eq(split), eq(LockModeType.PESSIMISTIC_WRITE), anyMap());
        orden.verify(entityManager).refresh(eq(gas), eq(LockModeType.PESSIMISTIC_WRITE), anyMap());
        orden.verify(entityManager).refresh(eq(cobre), eq(LockModeType.PESSIMISTIC_WRITE), anyMap());
    }

    @Test
    void sumarStockDevuelveLoAnulado() {
        service.sumarStock(Map.of(17L, 3, 1L, 1));

        assertThat(gas.getStockActual()).isEqualTo(13);
        assertThat(split.getStockActual()).isZero();
    }

    @Test
    void editarUnProductoNoCambiaSuStock() {
        when(productoRepository.findById(17L)).thenReturn(java.util.Optional.of(gas));
        when(productoRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(categoriaService.obtenerPorId(any())).thenReturn(new com.bomerp.catalogo.categoria.entity.Categoria());

        Producto editado = service.actualizar(17L, new com.bomerp.catalogo.producto.dto.ProductoRequestDTO(
                "Gas R410A 11.3 kg", null, 5L, Producto.Sector.MATERIAL_SERVICIO, new java.math.BigDecimal("480"),
                new java.math.BigDecimal("25"), Producto.Moneda.SOLES, 999, 6, true));

        assertThat(editado.getStockActual()).isEqualTo(10);
        assertThat(editado.getNombre()).isEqualTo("Gas R410A 11.3 kg");
    }
}
