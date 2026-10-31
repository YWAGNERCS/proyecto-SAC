package com.bomerp.cobranza;

import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.clientes.cliente.service.ClienteService;
import com.bomerp.clientes.general.integration.Cliente;
import com.bomerp.cobranza.cobro.dto.CobroRequestDTO;
import com.bomerp.cobranza.cobro.entity.Cobro;
import com.bomerp.cobranza.cobro.entity.DetalleCobro;
import com.bomerp.cobranza.cobro.repository.CobroRepository;
import com.bomerp.cobranza.cobro.service.CobranzaService;
import com.bomerp.config.ReglaNegocioException;
import com.bomerp.facturacion.facturacion.service.FacturacionService;
import com.bomerp.ventas.detalleventa.entity.DetalleVenta;
import com.bomerp.ventas.venta.entity.Venta;
import com.bomerp.ventas.venta.service.VentaService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** RF-20 y RF-23: abonos a ventas a crédito, voucher duplicado y anulación de cobros. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CobranzaServiceTest {

    @Mock CobroRepository cobroRepository;
    @Mock VentaService ventaService;
    @Mock ClienteService clienteService;
    @Mock FacturacionService facturacionService;
    @InjectMocks CobranzaService service;

    private Cliente cliente;
    private Venta venta;

    @BeforeEach
    void ventaACreditoDe118() {
        cliente = new Cliente();
        cliente.setId(5L);
        cliente.setNombreORazonSocial("Cliente de prueba");
        Producto p = new Producto();
        p.setSector(Producto.Sector.MATERIAL_SERVICIO);
        p.setMoneda(Producto.Moneda.SOLES);
        venta = new Venta();
        venta.setId(20L);
        venta.setCliente(cliente);
        venta.setMoneda(Venta.Moneda.SOLES);
        venta.setFechaVenta(LocalDate.now().minusDays(10));
        venta.setEstadoPago(Venta.EstadoPago.PENDIENTE);
        venta.agregarDetalle(new DetalleVenta(p, 1, new BigDecimal("100")));   // total 118 con IGV
        when(clienteService.obtenerPorId(5L)).thenReturn(cliente);
        when(ventaService.obtenerPorIdPublico(20L)).thenReturn(venta);
        when(cobroRepository.totalCobradoDeVenta(20L)).thenReturn(BigDecimal.ZERO);
        when(cobroRepository.vigentesConReferencia(any(), any())).thenReturn(List.of());
        when(cobroRepository.save(any(Cobro.class))).thenAnswer(i -> {
            Cobro c = i.getArgument(0);
            c.setId(1L);
            return c;
        });
    }

    private CobroRequestDTO cobro(String monto, String referencia, LocalDate fecha) {
        return new CobroRequestDTO(5L, fecha, Venta.Moneda.SOLES, Cobro.MetodoPago.TRANSFERENCIA, referencia, null,
                List.of(new CobroRequestDTO.AbonoDTO(20L, new BigDecimal(monto))));
    }

    @Test
    void unAbonoParcialNoMarcaLaVentaPagada() {
        service.registrar(cobro("50", "OP-1", null), "lucy");

        verify(ventaService, never()).marcarComoPagada(anyLong());
    }

    @Test
    void cubrirElSaldoMarcaLaVentaPagada() {
        service.registrar(cobro("118", "OP-2", null), "lucy");

        verify(ventaService).marcarComoPagada(20L);
    }

    @Test
    void noSeCobraMasQueElSaldo() {
        when(cobroRepository.totalCobradoDeVenta(20L)).thenReturn(new BigDecimal("100"));

        assertThatThrownBy(() -> service.registrar(cobro("20", "OP-3", null), "lucy"))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("supera el saldo");
    }

    @Test
    void elMismoVoucherNoEntraDosVeces() {
        Cobro previo = new Cobro();
        previo.setId(9L);
        previo.setFecha(LocalDate.now());
        when(cobroRepository.vigentesConReferencia(Cobro.MetodoPago.TRANSFERENCIA, "OP-4")).thenReturn(List.of(previo));

        assertThatThrownBy(() -> service.registrar(cobro("10", "OP-4", null), "lucy"))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("cobro #9");
    }

    @Test
    void elCobroNoPuedeSerAnteriorALaVentaNiFuturo() {
        assertThatThrownBy(() -> service.registrar(cobro("10", "OP-5", venta.getFechaVenta().minusDays(1)), "lucy"))
                .hasMessageContaining("anterior a la venta");
        assertThatThrownBy(() -> service.registrar(cobro("10", "OP-6", LocalDate.now().plusDays(1)), "lucy"))
                .hasMessageContaining("futura");
    }

    @Test
    void noSeCobraUnaVentaAnulada() {
        venta.setEstado(Venta.EstadoVenta.ANULADA);

        assertThatThrownBy(() -> service.registrar(cobro("10", "OP-7", null), "lucy")).hasMessageContaining("ANULADA");
    }

    private Cobro cobroVigente() {
        Cobro c = new Cobro();
        c.setId(3L);
        c.setCliente(cliente);
        c.setFecha(LocalDate.now());
        c.setMoneda(Venta.Moneda.SOLES);
        c.setMetodoPago(Cobro.MetodoPago.TRANSFERENCIA);
        c.setMontoTotal(new BigDecimal("118"));
        c.agregarDetalle(new DetalleCobro(venta, new BigDecimal("118")));
        when(cobroRepository.findById(3L)).thenReturn(Optional.of(c));
        return c;
    }

    @Test
    void anularUnCobroDevuelveLaVentaAPendiente() {
        Cobro c = cobroVigente();
        venta.setEstadoPago(Venta.EstadoPago.PAGADO);

        service.anular(3L, "pago registrado dos veces", "lisandro");

        assertThat(c.getEstado()).isEqualTo(Cobro.Estado.ANULADO);
        assertThat(c.getAnuladoPor()).isEqualTo("lisandro");
        verify(ventaService).volverAPendiente(eq(20L));
    }

    @Test
    void anularPideMotivoYNoSeAnulaDosVeces() {
        Cobro c = cobroVigente();
        assertThatThrownBy(() -> service.anular(3L, "no", "lisandro")).hasMessageContaining("motivo");

        c.setEstado(Cobro.Estado.ANULADO);
        assertThatThrownBy(() -> service.anular(3L, "pago duplicado", "lisandro")).hasMessageContaining("ya fue anulado");
    }

    @Test
    void elCobroDeUnaVentaAnuladaVaPorDevoluciones() {
        cobroVigente();
        venta.setEstado(Venta.EstadoVenta.ANULADA);

        assertThatThrownBy(() -> service.anular(3L, "venta anulada", "lisandro")).hasMessageContaining("Devoluciones");
    }
}
