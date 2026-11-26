package com.bomerp.reportes;

import com.bomerp.config.exportacion.ExcelBuilder;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;
import static com.bomerp.config.exportacion.ExcelBuilder.*;

class ExcelPresentacionTest {
    @Test void conservaTiposTotalesFiltrosYLogoEnHojasConDatosYVacias() throws Exception {
        var excel = new ExcelBuilder();
        var ventas = excel.hoja("Ventas", "Reporte de ventas", "Datos de ejemplo / noviembre 2026",
                entero("Venta"), fecha("Fecha"), texto("Cliente"), texto("RUC"), texto("Moneda"),
                moneda("Base"), moneda("IGV"), moneda("Total"));
        ventas.fila(451, LocalDate.of(2026,11,26), "Frío Andino S.A.C. (demostración)", "00999990011", "SOLES",
                new BigDecimal("720"), new BigDecimal("129.60"), new BigDecimal("849.60"));
        ventas.fila(452, LocalDate.of(2026,11,26), "Cliente de demostración con nombre largo para verificar el ajuste de texto en la exportación", "20999990012", "SOLES",
                new BigDecimal("100"), new BigDecimal("18"), new BigDecimal("118"));
        ventas.filaTotal("Total vigente en soles", null, null, null, "SOLES", new BigDecimal("820"), new BigDecimal("147.60"), new BigDecimal("967.60"));
        excel.hoja("Compras", "Reporte de compras", "Datos de ejemplo",
                texto("Proveedor"), fecha("Fecha"), moneda("Importe"))
                .fila("Proveedor de demostración",LocalDate.of(2026,11,26),new BigDecimal("118"));
        excel.hoja("Kardex", "Kardex de materiales", "Datos de ejemplo",
                fecha("Fecha"), texto("Movimiento"), entero("Entrada"), entero("Salida"), entero("Saldo"))
                .fila(LocalDate.of(2026,11,26),"COMPRA",5,null,5)
                .fila(LocalDate.of(2026,11,26),"VENTA",null,1,4)
                .filaTotal("Totales del periodo",null,5,1,4);
        excel.hoja("Sin movimientos", "Reporte sin movimientos", "Sin registros en el periodo",
                texto("Documento"), moneda("Total"));
        byte[] bytes = excel.generar();
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = wb.getSheet("Ventas");
            int first = FILA_ENCABEZADO + 1;
            assertThat(sheet.getRow(first).getCell(1).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(DateUtil.isCellDateFormatted(sheet.getRow(first).getCell(1))).isTrue();
            assertThat(sheet.getRow(first).getCell(3).getStringCellValue()).isEqualTo("00999990011");
            assertThat(sheet.getRow(first).getCell(7).getNumericCellValue()).isEqualTo(849.60);
            assertThat(sheet.getRow(first+3).getCell(7).getNumericCellValue()).isEqualTo(967.60);
            assertThat(sheet.getCTWorksheet().getAutoFilter().getRef()).isEqualTo("A7:H9");
            assertThat(sheet.getRow(first).getCell(7).getCellStyle().getDataFormatString()).isEqualTo("#,##0.00");
            assertThat(sheet.getRow(first).getCell(0).getCellStyle().getIndex())
                    .isNotEqualTo(sheet.getRow(first+1).getCell(0).getCellStyle().getIndex());
            assertThat(sheet.isDisplayGridlines()).isFalse();
            assertThat(sheet.getPrintSetup().getFitWidth()).isEqualTo((short)1);
            assertThat(wb.getAllPictures()).hasSize(1);
            for (Sheet s : wb) {
                var picture = (org.apache.poi.xssf.usermodel.XSSFPicture)s.getDrawingPatriarch().iterator().next();
                assertThat(picture.getClientAnchor().getRow2()).isGreaterThan(picture.getClientAnchor().getRow1());
                assertThat(picture.getClientAnchor().getCol2() > 0 || picture.getClientAnchor().getDx2() > 0).isTrue();
            }
            assertThat(wb.getSheet("Sin movimientos").getLastRowNum()).isEqualTo(FILA_ENCABEZADO);
        }
        Files.write(Path.of("target/excel_presentacion_demo.xlsx"), bytes);
    }
}
