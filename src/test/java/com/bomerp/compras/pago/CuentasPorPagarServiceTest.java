package com.bomerp.compras.pago;

import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.compras.compra.entity.Compra;
import com.bomerp.compras.compra.repository.CompraRepository;
import com.bomerp.compras.pago.entity.PagoProveedor;
import com.bomerp.compras.pago.repository.PagoProveedorRepository;
import com.bomerp.compras.pago.service.CuentasPorPagarService;
import com.bomerp.config.ReglaNegocioException;
import com.bomerp.proveedores.proveedor.entity.Proveedor;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** RF-30: reglas de los pagos a proveedores (sin base de datos: repositorios simulados). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CuentasPorPagarServiceTest {

    @Mock PagoProveedorRepository pagoRepository;
    @Mock CompraRepository compraRepository;
    @InjectMocks CuentasPorPagarService service;

    private Compra compra;

    @BeforeEach
    void compraACredito() {
        Proveedor proveedor = new Proveedor();
        proveedor.setId(1L);
        proveedor.setNombreORazonSocial("Proveedor de prueba");
        compra = new Compra();
        compra.setId(10L);
        compra.setProveedor(proveedor);
        compra.setFechaCompra(LocalDate.now().minusDays(3));
        compra.setNumeroFactura("F001-00000001");
        compra.setMoneda(Producto.Moneda.SOLES);
        compra.setTotal(new BigDecimal("100.00"));
        compra.setTipoPago(Compra.TipoPago.CREDITO);
        compra.setEstadoPago(Compra.EstadoPago.PENDIENTE);
        when(compraRepository.findById(10L)).thenReturn(Optional.of(compra));
        when(pagoRepository.pagadoDeCompra(10L)).thenReturn(BigDecimal.ZERO);
        when(pagoRepository.mismaOperacion(anyLong(), any(), anyString())).thenReturn(List.of());
        when(pagoRepository.save(any(PagoProveedor.class))).thenAnswer(i -> {
            PagoProveedor p = i.getArgument(0);
            p.setId(1L);
            return p;
        });
    }

    private CuentasPorPagarService.PagoRequest pago(String monto, String operacion) {
        return new CuentasPorPagarService.PagoRequest(10L, LocalDate.now(), new BigDecimal(monto),
                PagoProveedor.Metodo.TRANSFERENCIA, operacion, null);
    }

    @Test
    void pagoParcialDejaLaCompraPendiente() {
        CuentasPorPagarService.PagoDTO p = service.registrar(pago("40", "OP-1"), "lucy");

        assertThat(p.monto()).isEqualByComparingTo("40");
        assertThat(p.registradoPor()).isEqualTo("lucy");
        assertThat(compra.getEstadoPago()).isEqualTo(Compra.EstadoPago.PENDIENTE);
        verify(compraRepository, never()).save(any());
    }

    @Test
    void pagarElSaldoCompletoLaMarcaPagada() {
        when(pagoRepository.pagadoDeCompra(10L)).thenReturn(new BigDecimal("40.00"));

        service.registrar(pago("60", "OP-2"), "lucy");

        assertThat(compra.getEstadoPago()).isEqualTo(Compra.EstadoPago.PAGADA);
    }

    @Test
    void noSePagaMasQueElSaldo() {
        assertThatThrownBy(() -> service.registrar(pago("100.01", "OP-3"), "lucy"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("supera el saldo");
    }

    @Test
    void unaCompraAlContadoNoTieneSaldo() {
        compra.setTipoPago(Compra.TipoPago.CONTADO);
        compra.setEstadoPago(Compra.EstadoPago.PAGADA);

        assertThatThrownBy(() -> service.registrar(pago("10", "OP-4"), "lucy"))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("al contado");
    }

    @Test
    void elPagoNoPuedeSerAnteriorALaCompra() {
        var anterior = new CuentasPorPagarService.PagoRequest(10L, compra.getFechaCompra().minusDays(1),
                new BigDecimal("10"), PagoProveedor.Metodo.TRANSFERENCIA, "OP-5", null);

        assertThatThrownBy(() -> service.registrar(anterior, "lucy")).hasMessageContaining("anterior a la compra");
    }

    @Test
    void unaTransferenciaPideNumeroDeOperacionPeroElEfectivoNo() {
        assertThatThrownBy(() -> service.registrar(pago("10", " "), "lucy")).hasMessageContaining("N° de operación");

        var efectivo = new CuentasPorPagarService.PagoRequest(10L, LocalDate.now(), new BigDecimal("10"),
                PagoProveedor.Metodo.EFECTIVO, null, null);
        assertThat(service.registrar(efectivo, "lucy").metodo()).isEqualTo("EFECTIVO");
    }

    @Test
    void elMismoNumeroDeOperacionNoEntraDosVeces() {
        PagoProveedor anterior = new PagoProveedor();
        anterior.setId(7L);
        anterior.setCompra(compra);
        // Se compara sin espacios y en mayúsculas: "op 1" y "OP1" son el mismo pago
        when(pagoRepository.mismaOperacion(1L, PagoProveedor.Metodo.TRANSFERENCIA, "OP1")).thenReturn(List.of(anterior));

        assertThatThrownBy(() -> service.registrar(pago("10", "op 1"), "lucy"))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("pago #7");
    }

    @Test
    void anularUnPagoDevuelveLaCompraAPendiente() {
        compra.setEstadoPago(Compra.EstadoPago.PAGADA);
        PagoProveedor p = new PagoProveedor();
        p.setId(3L);
        p.setCompra(compra);
        p.setMonto(new BigDecimal("100"));
        p.setMetodo(PagoProveedor.Metodo.TRANSFERENCIA);
        p.setFechaPago(LocalDate.now());
        when(pagoRepository.findById(3L)).thenReturn(Optional.of(p));

        CuentasPorPagarService.PagoDTO anulado = service.anular(3L, "pago duplicado", "lisandro");

        assertThat(anulado.estado()).isEqualTo("ANULADO");
        assertThat(anulado.anuladoPor()).isEqualTo("lisandro");
        assertThat(compra.getEstadoPago()).isEqualTo(Compra.EstadoPago.PENDIENTE);
        assertThatThrownBy(() -> service.anular(3L, "otra vez", "lisandro")).hasMessageContaining("ya está anulado");
    }
}
