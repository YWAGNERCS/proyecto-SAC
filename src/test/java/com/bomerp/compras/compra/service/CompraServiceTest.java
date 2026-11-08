package com.bomerp.compras.compra.service;

import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.catalogo.producto.service.ProductoService;
import com.bomerp.compras.compra.dto.CompraRequestDTO;
import com.bomerp.compras.compra.entity.Compra;
import com.bomerp.compras.compra.repository.CompraRepository;
import com.bomerp.config.ReglaNegocioException;
import com.bomerp.proveedores.proveedor.entity.Proveedor;
import com.bomerp.proveedores.proveedor.service.ProveedorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Compras: N° de factura, factura duplicada, stock de materiales y forma de pago (RF-30). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CompraServiceTest {

    @Mock CompraRepository compraRepository;
    @Mock ProveedorService proveedorService;
    @Mock ProductoService productoService;
    @Mock com.bomerp.config.tipocambio.TipoCambioService tipoCambioService;
    @InjectMocks CompraService service;

    private Producto material;
    private Producto equipo;

    @BeforeEach
    void preparar() {
        Proveedor proveedor = new Proveedor();
        proveedor.setId(1L);
        proveedor.setNombreORazonSocial("Daikin");
        when(proveedorService.obtenerPorId(1L)).thenReturn(proveedor);
        material = new Producto();
        material.setId(18L);
        material.setSector(Producto.Sector.MATERIAL_SERVICIO);
        material.setStockActual(5);
        material.setMoneda(Producto.Moneda.SOLES);
        equipo = new Producto();
        equipo.setId(1L);
        equipo.setSector(Producto.Sector.CLIMATIZACION);
        equipo.setStockActual(0);
        when(productoService.obtenerPorId(18L)).thenReturn(material);
        when(productoService.obtenerPorId(1L)).thenReturn(equipo);
        when(compraRepository.findFirstByProveedor_IdAndNumeroFactura(anyLong(), anyString())).thenReturn(Optional.empty());
        when(compraRepository.save(any(Compra.class))).thenAnswer(i -> i.getArgument(0));
    }

    private CompraRequestDTO compra(Compra.TipoPago tipo, Integer dias) {
        return new CompraRequestDTO(1L, LocalDate.of(2026, 10, 1), "f001-123", Producto.Moneda.SOLES, null, tipo, dias,
                List.of(new CompraRequestDTO.DetalleCompraRequestDTO(18L, 3, new BigDecimal("10.005")),
                        new CompraRequestDTO.DetalleCompraRequestDTO(1L, 1, new BigDecimal("1000"))));
    }

    @Test
    void elNumeroDeFacturaSeNormaliza() {
        assertThat(CompraService.normalizarFactura(" f001-123 ")).isEqualTo("F001-00000123");
        assertThatThrownBy(() -> CompraService.normalizarFactura("123")).hasMessageContaining("SERIE-NÚMERO");
    }

    @Test
    void alContadoQuedaPagadaYSubeSoloElStockDeMateriales() {
        Compra c = service.registrarCompra(compra(null, null), "lucy");

        assertThat(c.getTipoPago()).isEqualTo(Compra.TipoPago.CONTADO);
        assertThat(c.getEstadoPago()).isEqualTo(Compra.EstadoPago.PAGADA);
        assertThat(c.getTotal()).isEqualByComparingTo("1030.03");   // 3 × 10.01 + 1 000
        // Sube el stock por la operación central (con bloqueo); ella ignora lo que no controla stock
        verify(productoService).sumarStock(java.util.Map.of(18L, 3, 1L, 1));
        assertThat(c.getRegistradoPor()).isEqualTo("lucy");
        // Auditoría M2 y M1: el total incluye el IGV; el material guarda su costo sin IGV (10.01 / 1.18)
        assertThat(c.getSubtotal()).isEqualByComparingTo("872.91");
        assertThat(c.getIgv()).isEqualByComparingTo("157.12");
        assertThat(material.getUltimoCostoCompra()).isEqualByComparingTo("8.48");
        assertThat(material.getFechaUltimaCompra()).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    void aCreditoQuedaPendienteYVence() {
        Compra c = service.registrarCompra(compra(Compra.TipoPago.CREDITO, 30), "lucy");

        assertThat(c.getEstadoPago()).isEqualTo(Compra.EstadoPago.PENDIENTE);
        assertThat(c.getFechaVencimiento()).isEqualTo(LocalDate.of(2026, 10, 31));
    }

    @Test
    void aCreditoSinDiasSeRechaza() {
        assertThatThrownBy(() -> service.registrarCompra(compra(Compra.TipoPago.CREDITO, null), "lucy"))
                .hasMessageContaining("días de crédito");
        assertThatThrownBy(() -> service.registrarCompra(compra(Compra.TipoPago.CREDITO, 400), "lucy"))
                .hasMessageContaining("1 a 365");
    }

    @Test
    void laMismaFacturaDelMismoProveedorNoEntraDosVeces() {
        Compra anterior = new Compra();
        anterior.setId(4L);
        when(compraRepository.findFirstByProveedor_IdAndNumeroFactura(1L, "F001-00000123")).thenReturn(Optional.of(anterior));

        assertThatThrownBy(() -> service.registrarCompra(compra(null, null), "lucy"))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("compra #4");
        verify(productoService, never()).sumarStock(any());
    }
}
