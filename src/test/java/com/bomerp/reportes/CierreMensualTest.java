package com.bomerp.reportes;
import com.bomerp.reportes.mensual.*;
import com.bomerp.compras.compra.entity.Compra;
import com.bomerp.compras.compra.service.CompraService;
import com.bomerp.proveedores.proveedor.entity.Proveedor;
import com.bomerp.ventas.venta.service.VentaService;
import com.bomerp.ventas.venta.entity.Venta;
import com.bomerp.ventas.detalleventa.entity.DetalleVenta;
import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.clientes.general.integration.Cliente;
import com.bomerp.facturacion.facturacion.service.FacturacionService;
import com.bomerp.facturacion.comprobante.entity.Comprobante;
import com.bomerp.facturacion.notacredito.entity.NotaCredito;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class CierreMensualTest {
    @Test void noDuplicaAnulacionYSeparaMonedasYAmbientes() throws Exception {
        var vs=mock(VentaService.class);var cs=mock(CompraService.class);var fs=mock(FacturacionService.class);
        var servicio=new CierreMensualService(vs,cs,fs);
        var cliente=new Cliente();cliente.setNombreORazonSocial("Cliente demo");
        var p=new Producto();p.setNombre("Servicio");
        var v=new Venta();v.setId(1L);v.setCliente(cliente);v.setFechaVenta(LocalDate.of(2026,11,1));v.setMoneda(Venta.Moneda.SOLES);v.setEstado(Venta.EstadoVenta.ANULADA);v.getDetalles().add(new DetalleVenta(p,1,new BigDecimal("1000")));
        var original=new Comprobante();original.setId(1L);original.setVenta(v);original.setSerie("F001");original.setNumero("00000001");original.setFechaEmision(v.getFechaVenta());original.setTotal(v.calcularTotal());original.setEstadoSunat(Comprobante.EstadoSunat.ACEPTADO);original.registrarAmbiente(Comprobante.AmbienteEmision.PRODUCCION);
        var beta=new Comprobante();beta.setId(2L);beta.setVenta(v);beta.setSerie("F001");beta.setNumero("00000002");beta.setFechaEmision(v.getFechaVenta());beta.setTotal(v.calcularTotal());beta.setEstadoSunat(Comprobante.EstadoSunat.ACEPTADO);beta.registrarAmbiente(Comprobante.AmbienteEmision.BETA);
        var nota=new NotaCredito();nota.setId(1L);nota.setComprobanteReferencia(original);nota.setSerie("FC01");nota.setNumero("00000001");nota.setFechaEmision(LocalDate.of(2026,11,20));nota.setTotal(v.calcularTotal());nota.setEstadoSunat(Comprobante.EstadoSunat.ACEPTADO);nota.setAmbienteEmision(Comprobante.AmbienteEmision.PRODUCCION);
        var compra=new Compra();compra.setId(1L);compra.setProveedor(new Proveedor());compra.setFechaCompra(LocalDate.of(2026,11,3));compra.setMoneda(Producto.Moneda.DOLARES);compra.fijarTotalConIgv(new BigDecimal("118"));
        when(vs.buscar(any(),any(),any(),any())).thenReturn(List.of(v));when(cs.buscar(any(),any(),any())).thenReturn(List.of(compra));
        when(fs.comprobantesEntre(any(),any())).thenReturn(List.of(original,beta));when(fs.notasEntre(any(),any())).thenReturn(List.of(nota));
        var r=servicio.obtener(2026,11);var pen=r.resumen().getFirst();var usd=r.resumen().getLast();
        assertThat(pen.ventasVigentes()).isZero();assertThat(pen.documentosValidos()).isEqualByComparingTo("1180");assertThat(pen.notasValidas()).isEqualByComparingTo("-1180");assertThat(pen.igvDocumentosNeto()).isZero();assertThat(pen.compras()).isZero();assertThat(usd.compras()).isEqualByComparingTo("118");
        assertThat(r.movimientos().stream().filter(x->"BETA".equals(x.ambiente())).findFirst().orElseThrow().incluido()).isFalse();
        // Nota de este mes sobre venta anterior: se descuenta en el mes de emisión.
        original.setFechaEmision(LocalDate.of(2026,10,15));when(fs.comprobantesEntre(any(),any())).thenReturn(List.of());when(vs.buscar(any(),any(),any(),any())).thenReturn(List.of());
        assertThat(servicio.obtener(2026,11).resumen().getFirst().igvDocumentosNeto()).isEqualByComparingTo("-180");
        var bytes=new CierreMensualController(servicio).exportar(2026,11).getBody();
        try(var wb=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))){assertThat(wb.getSheet("Movimientos").getLastRowNum()).isEqualTo(com.bomerp.config.exportacion.ExcelBuilder.FILA_ENCABEZADO + 2);assertThat(wb.getSheet("Resumen")).isNotNull();}
        java.nio.file.Files.write(java.nio.file.Path.of("target/cierre_mensual_demo.xlsx"),bytes);
        assertThatThrownBy(()->servicio.obtener(2026,13)).isInstanceOf(IllegalArgumentException.class);
    }
}
