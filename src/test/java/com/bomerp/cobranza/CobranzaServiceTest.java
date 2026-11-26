package com.bomerp.cobranza;

import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.clientes.cliente.service.ClienteService;
import com.bomerp.clientes.general.integration.Cliente;
import com.bomerp.cobranza.cobro.dto.CobroRequestDTO;
import com.bomerp.cobranza.cobro.entity.Cobro;
import com.bomerp.cobranza.cobro.repository.CobroRepository;
import com.bomerp.cobranza.cobro.service.CobranzaService;
import com.bomerp.config.ReglaNegocioException;
import com.bomerp.ventas.venta.service.ComprobantesDeVenta;
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
    @Mock ComprobantesDeVenta comprobantesDeVenta;
    @org.mockito.Spy com.bomerp.ventas.tributos.DeduccionTributariaService tributos =
            new com.bomerp.ventas.tributos.DeduccionTributariaService(new com.bomerp.ventas.tributos.TributosProperties(
                    new BigDecimal("12"), new BigDecimal("3"), new BigDecimal("700")));
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
    void cobranzaSepara102DeDetraccionY747Con60DePagoDirectoSegunElModeloJD() {
        venta.setId(null); cliente.setTipo(Cliente.TipoCliente.EMPRESA);
        venta.getDetalles().clear();
        Producto p = new Producto(); p.setSector(Producto.Sector.SERVICIO);
        p.setClasificacionDetraccion(com.bomerp.catalogo.producto.entity.ClasificacionDetraccion.OTROS_SERVICIOS_EMPRESARIALES);
        venta.agregarDetalle(new DetalleVenta(p, 1, new BigDecimal("720")));
        tributos.aplicar(venta); venta.setId(20L);
        when(ventaService.listarPendientesDeCobro(5L)).thenReturn(List.of(venta));
        when(comprobantesDeVenta.numeroComprobanteDeVenta(20L)).thenReturn(Optional.of("F001-DEMO"));
        var cuenta = service.porCobrar(5L).getFirst();
        assertThat(cuenta.total()).isEqualByComparingTo("849.60");
        assertThat(cuenta.deduccionPendiente()).isEqualByComparingTo("102.00");
        assertThat(cuenta.montoDetraccionSoles()).isEqualByComparingTo("102.00");
        assertThat(cuenta.netoPorCobrar()).isEqualByComparingTo("747.60");
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
        c.abonar(venta, new BigDecimal("118"));
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

    private void prepararRetencion() {
        venta.setTipoDeduccion(Venta.TipoDeduccion.RETENCION);
        venta.setPorcentajeDeduccion(new BigDecimal("3"));
        venta.setMontoDeduccion(new BigDecimal("35.40"));
        when(comprobantesDeVenta.numeroComprobanteDeVenta(20L)).thenReturn(Optional.of("F001-20"));
        cliente.setTipo(Cliente.TipoCliente.EMPRESA);
        cliente.setAgenteRetencion(true);
        venta.getDetalles().getFirst().setPrecioUnitario(new BigDecimal("1000"));
    }
    private CobroRequestDTO retencion(String monto, String base) {
        return new CobroRequestDTO(5L, null, Venta.Moneda.SOLES, Cobro.MetodoPago.RETENCION, "RET-1", null,
                List.of(new CobroRequestDTO.AbonoDTO(20L, new BigDecimal(monto), base == null ? null : new BigDecimal(base))));
    }
    @Test
    void retencionParcialSeCalculaSobreElPagoYConservaLaBase() {
        prepararRetencion();
        var respuesta = service.registrar(retencion("15", "500"), "lucy");
        assertThat(respuesta.abonos().getFirst().baseRetencion()).isEqualByComparingTo("500");
        assertThat(respuesta.montoTotal()).isEqualByComparingTo("15");
        verify(ventaService, never()).marcarComoPagada(anyLong());
    }
    @Test
    void rechazaRetenerTodaLaFacturaCuandoElPagoEsParcial() {
        prepararRetencion();
        assertThatThrownBy(() -> service.registrar(retencion("35.40", "500"), "lucy"))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("15.00");
        verify(cobroRepository, never()).save(any());
    }
    @Test
    void exigeBaseDelPagoEnLaRetencion() {
        prepararRetencion();
        assertThatThrownBy(() -> service.registrar(retencion("15", null), "lucy"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("importe bruto");
    }
    @Test
    void noAdmiteRetencionEnBoletaNiSobreDetraccion() {
        prepararRetencion();
        when(comprobantesDeVenta.numeroComprobanteDeVenta(20L)).thenReturn(Optional.of("B001-12"));
        assertThatThrownBy(() -> service.registrar(retencion("15", "500"), "lucy"))
                .isInstanceOf(ReglaNegocioException.class);
        when(comprobantesDeVenta.numeroComprobanteDeVenta(20L)).thenReturn(Optional.of("F001-12"));
        venta.setTipoDeduccion(Venta.TipoDeduccion.DETRACCION);
        assertThatThrownBy(() -> service.registrar(retencion("15", "500"), "lucy"))
                .isInstanceOf(ReglaNegocioException.class);
    }
    @Test
    void noPermiteCrearRetencionNuevaAgrupandoFacturas() {
        when(comprobantesDeVenta.numeroComprobanteDeVenta(20L)).thenReturn(Optional.of("F001-20"));
        when(comprobantesDeVenta.numeroComprobanteDeVenta(21L)).thenReturn(Optional.of("F001-21"));
        cliente.setTipo(Cliente.TipoCliente.EMPRESA);
        cliente.setAgenteRetencion(true);
        venta.getDetalles().getFirst().setPrecioUnitario(new BigDecimal("400"));
        Venta otra = new Venta();
        otra.setId(21L); otra.setCliente(cliente); otra.setMoneda(Venta.Moneda.SOLES);
        otra.setFechaVenta(venta.getFechaVenta()); otra.setEstadoPago(Venta.EstadoPago.PENDIENTE);
        otra.agregarDetalle(new DetalleVenta(venta.getDetalles().getFirst().getProducto(), 1, new BigDecimal("400")));
        when(ventaService.obtenerPorIdPublico(21L)).thenReturn(otra);
        when(cobroRepository.totalCobradoDeVenta(21L)).thenReturn(BigDecimal.ZERO);
        var dto = new CobroRequestDTO(5L, null, Venta.Moneda.SOLES, Cobro.MetodoPago.RETENCION, "RET-GRUPO", null,
                List.of(new CobroRequestDTO.AbonoDTO(20L, new BigDecimal("14.16"), new BigDecimal("472")),
                        new CobroRequestDTO.AbonoDTO(21L, new BigDecimal("14.16"), new BigDecimal("472"))));
        assertThatThrownBy(() -> service.registrar(dto, "lucy"))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("ventas nuevas");
        verify(cobroRepository, never()).save(any());
    }

    @Test
    void noAcreditaRetencionSiTodaviaNoExisteFactura() {
        prepararRetencion();
        when(comprobantesDeVenta.numeroComprobanteDeVenta(20L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.registrar(retencion("15", "500"), "lucy"))
                .isInstanceOf(ReglaNegocioException.class);
        verify(cobroRepository, never()).save(any());
    }
}
