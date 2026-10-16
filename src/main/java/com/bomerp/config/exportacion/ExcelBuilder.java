package com.bomerp.config.exportacion;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Arma archivos Excel (.xlsx) con un formato común para todos los módulos:
 * título, subtítulo (filtros y fecha de generación), encabezados con color,
 * montos con 2 decimales, fechas dd/mm/aaaa, autofiltro y encabezado fijo.
 *
 * Uso:
 *   ExcelBuilder excel = new ExcelBuilder();
 *   excel.hoja("Ventas", "Reporte de ventas", "Del 01/10 al 13/10",
 *           ExcelBuilder.texto("Cliente"), ExcelBuilder.moneda("Total"))
 *        .fila("SPSA", new BigDecimal("1200.50"));
 *   return ExcelBuilder.descargar(excel.generar(), "ventas.xlsx");
 */
public class ExcelBuilder {

    public enum Tipo { TEXTO, ENTERO, MONEDA, FECHA }

    public record Columna(String titulo, Tipo tipo) {}

    public static Columna texto(String titulo) { return new Columna(titulo, Tipo.TEXTO); }
    public static Columna entero(String titulo) { return new Columna(titulo, Tipo.ENTERO); }
    public static Columna moneda(String titulo) { return new Columna(titulo, Tipo.MONEDA); }
    public static Columna fecha(String titulo) { return new Columna(titulo, Tipo.FECHA); }

    private static final int FILA_ENCABEZADO = 3;
    private static final DateTimeFormatter GENERADO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final Workbook libro = new XSSFWorkbook();
    private final List<Hoja> hojas = new ArrayList<>();
    private final CellStyle estiloTitulo;
    private final CellStyle estiloSubtitulo;
    private final CellStyle estiloEncabezado;
    private final CellStyle estiloMoneda;
    private final CellStyle estiloFecha;
    private final CellStyle estiloEntero;
    private final CellStyle estiloTotalTexto;
    private final CellStyle estiloTotalMoneda;

    public ExcelBuilder() {
        DataFormat formato = libro.createDataFormat();

        Font fuenteTitulo = libro.createFont();
        fuenteTitulo.setBold(true);
        fuenteTitulo.setFontHeightInPoints((short) 14);
        estiloTitulo = libro.createCellStyle();
        estiloTitulo.setFont(fuenteTitulo);

        Font fuenteGris = libro.createFont();
        fuenteGris.setItalic(true);
        fuenteGris.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        estiloSubtitulo = libro.createCellStyle();
        estiloSubtitulo.setFont(fuenteGris);

        Font fuenteBlanca = libro.createFont();
        fuenteBlanca.setBold(true);
        fuenteBlanca.setColor(IndexedColors.WHITE.getIndex());
        estiloEncabezado = libro.createCellStyle();
        estiloEncabezado.setFont(fuenteBlanca);
        estiloEncabezado.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        estiloEncabezado.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        estiloEncabezado.setAlignment(HorizontalAlignment.CENTER);
        estiloEncabezado.setVerticalAlignment(VerticalAlignment.CENTER);
        estiloEncabezado.setWrapText(true);

        estiloMoneda = libro.createCellStyle();
        estiloMoneda.setDataFormat(formato.getFormat("#,##0.00"));
        estiloFecha = libro.createCellStyle();
        estiloFecha.setDataFormat(formato.getFormat("dd/mm/yyyy"));
        estiloFecha.setAlignment(HorizontalAlignment.CENTER);
        estiloEntero = libro.createCellStyle();
        estiloEntero.setDataFormat(formato.getFormat("#,##0"));

        Font fuenteNegrita = libro.createFont();
        fuenteNegrita.setBold(true);
        estiloTotalTexto = libro.createCellStyle();
        estiloTotalTexto.setFont(fuenteNegrita);
        estiloTotalTexto.setBorderTop(BorderStyle.THIN);
        estiloTotalMoneda = libro.createCellStyle();
        estiloTotalMoneda.cloneStyleFrom(estiloMoneda);
        estiloTotalMoneda.setFont(fuenteNegrita);
        estiloTotalMoneda.setBorderTop(BorderStyle.THIN);
    }

    public Hoja hoja(String nombre, String titulo, String subtitulo, Columna... columnas) {
        Hoja hoja = new Hoja(libro.createSheet(nombre), titulo, subtitulo, columnas);
        hojas.add(hoja);
        return hoja;
    }

    /** Genera el archivo .xlsx en memoria. */
    public byte[] generar() {
        hojas.forEach(Hoja::cerrar);
        try (libro; ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el archivo Excel", e);
        }
    }

    /** Respuesta HTTP para que el navegador descargue el archivo. */
    public static ResponseEntity<byte[]> descargar(byte[] contenido, String nombreArchivo) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(nombreArchivo).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(contenido);
    }

    /** "del 01/10/2026 al 13/10/2026", "hasta el ...", o "todo el historial". */
    public static String describirPeriodo(LocalDate desde, LocalDate hasta) {
        DateTimeFormatter f = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        if (desde != null && hasta != null) return "del " + desde.format(f) + " al " + hasta.format(f);
        if (desde != null) return "desde el " + desde.format(f);
        if (hasta != null) return "hasta el " + hasta.format(f);
        return "todo el historial";
    }

    public class Hoja {
        private final Sheet hoja;
        private final Columna[] columnas;
        private int siguienteFila = FILA_ENCABEZADO + 1;
        private int filasTotal = 0;

        private Hoja(Sheet hoja, String titulo, String subtitulo, Columna[] columnas) {
            this.hoja = hoja;
            this.columnas = columnas;

            Cell celdaTitulo = hoja.createRow(0).createCell(0);
            celdaTitulo.setCellValue(titulo);
            celdaTitulo.setCellStyle(estiloTitulo);

            Cell celdaSub = hoja.createRow(1).createCell(0);
            celdaSub.setCellValue("JD Refrigeración S.A.C. · " + subtitulo
                    + " · generado el " + LocalDateTime.now().format(GENERADO));
            celdaSub.setCellStyle(estiloSubtitulo);

            Row encabezado = hoja.createRow(FILA_ENCABEZADO);
            encabezado.setHeightInPoints(30);
            for (int i = 0; i < columnas.length; i++) {
                Cell c = encabezado.createCell(i);
                c.setCellValue(columnas[i].titulo());
                c.setCellStyle(estiloEncabezado);
            }
            hoja.createFreezePane(0, FILA_ENCABEZADO + 1);
        }

        /** Agrega una fila; cada valor se formatea según el tipo de su columna. null = celda vacía. */
        public Hoja fila(Object... valores) {
            Row fila = hoja.createRow(siguienteFila++);
            for (int i = 0; i < valores.length && i < columnas.length; i++) {
                escribir(fila.createCell(i), valores[i], columnas[i].tipo(), false);
            }
            return this;
        }

        /** Fila de totales en negrita (sin autofiltro). */
        public Hoja filaTotal(Object... valores) {
            // Deja una fila en blanco entre los datos y el primer total
            Row fila = hoja.createRow(siguienteFila + 1 + filasTotal++);
            for (int i = 0; i < valores.length && i < columnas.length; i++) {
                escribir(fila.createCell(i), valores[i], columnas[i].tipo(), true);
            }
            return this;
        }

        public int cantidadFilas() {
            return siguienteFila - FILA_ENCABEZADO - 1;
        }

        private void escribir(Cell celda, Object valor, Tipo tipo, boolean total) {
            if (valor == null) {
                if (total) celda.setCellStyle(estiloTotalTexto);
                return;
            }
            if (valor instanceof LocalDate d) {
                celda.setCellValue(d);
                celda.setCellStyle(estiloFecha);
            } else if (valor instanceof Number n && tipo != Tipo.TEXTO) {
                celda.setCellValue(n instanceof BigDecimal b ? b.doubleValue() : n.doubleValue());
                celda.setCellStyle(tipo == Tipo.ENTERO ? estiloEntero : (total ? estiloTotalMoneda : estiloMoneda));
            } else {
                celda.setCellValue(String.valueOf(valor));
                if (total) celda.setCellStyle(estiloTotalTexto);
            }
        }

        private void cerrar() {
            if (cantidadFilas() > 0) {
                hoja.setAutoFilter(new CellRangeAddress(FILA_ENCABEZADO, siguienteFila - 1, 0, columnas.length - 1));
            }
            for (int i = 0; i < columnas.length; i++) {
                hoja.autoSizeColumn(i);
                // Límites para que un nombre muy largo no deje una columna inmanejable
                int ancho = Math.max(hoja.getColumnWidth(i), 12 * 256);
                hoja.setColumnWidth(i, Math.min(ancho + 512, 60 * 256));
            }
        }
    }
}
