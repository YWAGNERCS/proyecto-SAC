package com.bomerp.reportes.mensual;

import com.bomerp.compras.compra.service.CompraService;
import com.bomerp.facturacion.comprobante.entity.Comprobante;
import com.bomerp.facturacion.facturacion.service.FacturacionService;

import com.bomerp.ventas.venta.entity.Venta;
import com.bomerp.ventas.venta.service.VentaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.*;
import java.util.*;

/** Resumen de control: separa operación comercial y documentos con validez acreditada. */
@Service
public class CierreMensualService {
    private final VentaService ventas;
    private final CompraService compras;
    private final FacturacionService facturacion;
    public CierreMensualService(VentaService ventas, CompraService compras, FacturacionService facturacion) {
        this.ventas=ventas; this.compras=compras; this.facturacion=facturacion;
    }
    public record Fila(String tipo, Long id, LocalDate fecha, String numero, String tercero, String moneda,
                       String estado, String ambiente, BigDecimal base, BigDecimal igv, BigDecimal total, boolean incluido) {}
    public record Resumen(String moneda, BigDecimal ventasVigentes, BigDecimal compras, BigDecimal igvVentasVigentes,
                          BigDecimal igvComprasRegistradas, BigDecimal documentosValidos, BigDecimal notasValidas,
                          BigDecimal igvDocumentosNeto, BigDecimal diferenciaIgvReferencial, BigDecimal diferenciaIgvOperativa) {}
    public record Reporte(String periodo, List<Resumen> resumen, List<Fila> movimientos, List<String> observaciones) {}
    @Transactional(readOnly = true)
    public Reporte obtener(int anio, int mes) {
        if(anio<2000 || anio>2100 || mes<1 || mes>12) throw new IllegalArgumentException("Indique un mes y año válidos (2000 a 2100).");
        YearMonth p=YearMonth.of(anio,mes); var desde=p.atDay(1); var hasta=p.atEndOfMonth();
        List<Fila> filas=new ArrayList<>();
        for(var v:ventas.buscar(null,desde,hasta,null)) filas.add(new Fila("VENTA",v.getId(),v.getFechaVenta(),"Venta #"+v.getId(),
                v.getCliente().getNombreORazonSocial(),v.getMoneda().name(),v.getEstado().name(),null,
                v.calcularSubtotal(),v.calcularIgv(),v.calcularTotal(),v.getEstado()==Venta.EstadoVenta.REGISTRADA));
        for(var c:compras.buscar(desde,hasta,null)) filas.add(new Fila("COMPRA",c.getId(),c.getFechaCompra(),c.getNumeroFactura(),
                c.getProveedor().getNombreORazonSocial(),c.getMoneda().name(),c.getEstadoPago().name(),null,
                c.getSubtotal()!=null?c.getSubtotal():com.bomerp.compras.compra.entity.Compra.sinIgv(c.getTotal()),
                c.getIgv()!=null?c.getIgv():c.getTotal().subtract(com.bomerp.compras.compra.entity.Compra.sinIgv(c.getTotal())),c.getTotal(),true));
        for(var c:facturacion.comprobantesEntre(desde,hasta)) filas.add(new Fila("COMPROBANTE",c.getId(),c.getFechaEmision(),
                c.getSerie()+"-"+c.getNumero(),c.getReceptorNombre(),c.getVenta().getMoneda().name(),c.getEstadoSunat().name(),
                c.getAmbienteEmision()==null?"NO_REGISTRADO":c.getAmbienteEmision().name(),c.getVenta().calcularSubtotal(),c.getVenta().calcularIgv(),c.getTotal(),
                c.getEstadoSunat().valido() && c.getAmbienteEmision()==Comprobante.AmbienteEmision.PRODUCCION && !c.isReemplazado()));
        for(var n:facturacion.notasEntre(desde,hasta)) {
            var c=n.getComprobanteReferencia(); var v=c.getVenta();
            filas.add(new Fila("NOTA_CREDITO",n.getId(),n.getFechaEmision(),n.getSerie()+"-"+n.getNumero()+" → "+n.getNumeroComprobanteReferencia(),
                    c.getReceptorNombre(),v.getMoneda().name(),n.getEstadoSunat().name(),
                    n.getAmbienteEmision()==null?"NO_REGISTRADO":n.getAmbienteEmision().name(),v.calcularSubtotal().negate(),v.calcularIgv().negate(),n.getTotal().negate(),
                    n.getEstadoSunat().valido() && n.getAmbienteEmision()==Comprobante.AmbienteEmision.PRODUCCION));
        }
        List<Resumen> resumen=new ArrayList<>();
        for(String moneda:List.of("SOLES","DOLARES")) {
            var f=filas.stream().filter(x->x.moneda().equals(moneda)&&x.incluido()).toList();
            BigDecimal iv=sumar(f,"COMPROBANTE",true).add(sumar(f,"NOTA_CREDITO",true));
            BigDecimal ic=sumar(f,"COMPRA",true);
            resumen.add(new Resumen(moneda,sumar(f,"VENTA",false),sumar(f,"COMPRA",false),sumar(f,"VENTA",true),ic,
                    sumar(f,"COMPROBANTE",false),sumar(f,"NOTA_CREDITO",false),iv,iv.subtract(ic),sumar(f,"VENTA",true).subtract(ic)));
        }
        filas.sort(Comparator.comparing(Fila::fecha).thenComparing(Fila::tipo).thenComparing(Fila::id));
        return new Reporte(p.toString(),resumen,filas,List.of(
                "Control mensual preliminar: no bloquea periodos ni presenta declaraciones a SUNAT.",
                "Ventas operativas: fecha de venta, excluye anuladas. Comprobantes y notas: fecha de emisión, solo aceptados/observados en PRODUCCION.",
                "Las notas se descuentan una sola vez en el bloque de documentos, incluso si la venta corresponde a otro mes; no se restan nuevamente de las ventas vigentes.",
                "El IGV de compras es el registrado, pendiente de validar requisitos de crédito fiscal y documentos con Lucy. La diferencia no equivale al impuesto definitivo.",
                "Monedas separadas: dólares no se suman a soles ni se declaran como impuesto a pagar. Falta acordar con Lucy su conversión fiscal y el Excel modelo.",
                "No incluye saldos fiscales anteriores, renta, percepciones ni compensaciones. Detracciones y cobros no reducen el IGV de la operación.",
                "Facturas, notas o ambientes pendientes, rechazados, BETA, SIMULADO o no registrados se muestran, pero no se incluyen como documentos válidos."));
    }
    private static BigDecimal sumar(List<Fila> filas,String tipo,boolean igv) {
        return filas.stream().filter(f->f.tipo().equals(tipo)).map(f->igv?f.igv():f.total()).reduce(BigDecimal.ZERO,BigDecimal::add).setScale(2,RoundingMode.HALF_UP);
    }
}
