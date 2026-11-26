package com.bomerp.reportes.mensual;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import com.bomerp.config.exportacion.ExcelBuilder;
import static com.bomerp.config.exportacion.ExcelBuilder.*;

@RestController
@RequestMapping("/api/v1/admin/reportes/cierre-mensual")
public class CierreMensualController {
    private final CierreMensualService servicio;
    public CierreMensualController(CierreMensualService servicio){this.servicio=servicio;}
    @GetMapping
    public CierreMensualService.Reporte obtener(@RequestParam int anio,@RequestParam int mes){return servicio.obtener(anio,mes);}
    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar(@RequestParam int anio,@RequestParam int mes) throws java.io.IOException {
        var r=servicio.obtener(anio,mes);
        ExcelBuilder excel = new ExcelBuilder();
        var resumen = excel.hoja("Resumen", "Cierre mensual", "Periodo " + r.periodo(),
                texto("Concepto"), texto("Moneda"), moneda("Importe"));
        for (var x : r.resumen()) {
            String[] etiquetas={"Ventas vigentes","Compras registradas","IGV ventas vigentes","IGV compras registrado","Comprobantes válidos","Notas válidas (negativo)","IGV documentos neto","Diferencia IGV documental referencial","Diferencia IGV operativa preliminar"};
            java.math.BigDecimal[] valores={x.ventasVigentes(),x.compras(),x.igvVentasVigentes(),x.igvComprasRegistradas(),x.documentosValidos(),x.notasValidas(),x.igvDocumentosNeto(),x.diferenciaIgvReferencial(),x.diferenciaIgvOperativa()};
            for (int i=0;i<etiquetas.length;i++) resumen.fila(etiquetas[i],x.moneda(),valores[i]);
        }
        r.observaciones().forEach(resumen::nota);
        var hoja = excel.hoja("Movimientos", "Movimientos del cierre mensual", "Periodo " + r.periodo(),
                texto("Tipo"), entero("ID"), fecha("Fecha"), texto("Documento / referencia"),
                texto("Cliente / proveedor"), texto("Moneda"), texto("Estado"), texto("Ambiente"),
                moneda("Base"), moneda("IGV"), moneda("Total"), texto("Incluido en su bloque"));
        for (var f : r.movimientos()) {
            hoja.fila(f.tipo(),f.id(),f.fecha(),f.numero(),f.tercero(),f.moneda(),f.estado(),f.ambiente(),
                    f.base(),f.igv(),f.total(),f.incluido());
        }
        return ExcelBuilder.descargar(excel.generar(), "cierre_mensual_" + r.periodo() + ".xlsx");
    }
}
