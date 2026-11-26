package com.bomerp.config.exportacion;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.io.InputStream;
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

    public enum Tipo { TEXTO, ENTERO, MONEDA, FECHA, TIPO_CAMBIO }

    public record Columna(String titulo, Tipo tipo) {}

    public static Columna texto(String titulo) { return new Columna(titulo, Tipo.TEXTO); }
    public static Columna entero(String titulo) { return new Columna(titulo, Tipo.ENTERO); }
    public static Columna moneda(String titulo) { return new Columna(titulo, Tipo.MONEDA); }
    public static Columna fecha(String titulo) { return new Columna(titulo, Tipo.FECHA); }
    public static Columna tipoCambio(String titulo) { return new Columna(titulo, Tipo.TIPO_CAMBIO); }

    public static final int FILA_ENCABEZADO = 6;
    private static final DateTimeFormatter GENERADO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final Workbook libro = new XSSFWorkbook();
    private final List<Hoja> hojas = new ArrayList<>();
    private final CellStyle estiloTitulo;
    private final CellStyle estiloSubtitulo;
    private final CellStyle estiloEncabezado;
    private final CellStyle estiloMoneda;
    private final CellStyle estiloFecha;
    private final CellStyle estiloEntero;
    private final CellStyle estiloTipoCambio;
    private final CellStyle estiloTotalTexto;
    private final CellStyle estiloTotalMoneda;
    private final CellStyle[][] estilosDatos = new CellStyle[2][Tipo.values().length];
    private final CellStyle[] estilosTotales = new CellStyle[Tipo.values().length];
    private int logo = -1;

    public ExcelBuilder() {
        DataFormat formato = libro.createDataFormat();
        libro.setMissingCellPolicy(Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);

        Font fuenteTitulo = libro.createFont();
        fuenteTitulo.setFontName("Arial");
        fuenteTitulo.setColor(IndexedColors.DARK_BLUE.getIndex());
        fuenteTitulo.setBold(true);
        fuenteTitulo.setFontHeightInPoints((short) 14);
        estiloTitulo = libro.createCellStyle();
        estiloTitulo.setFont(fuenteTitulo);

        Font fuenteGris = libro.createFont();
        fuenteGris.setFontName("Arial");
        fuenteGris.setFontHeightInPoints((short) 10);
        fuenteGris.setItalic(true);
        fuenteGris.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        estiloSubtitulo = libro.createCellStyle();
        estiloSubtitulo.setFont(fuenteGris);

        Font fuenteBlanca = libro.createFont();
        fuenteBlanca.setFontName("Arial");
        fuenteBlanca.setFontHeightInPoints((short) 10);
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
        estiloTipoCambio = libro.createCellStyle();
        estiloTipoCambio.setDataFormat(formato.getFormat("0.000"));
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

        // Una misma identidad para todos los reportes, con estilos reutilizados por libro.
        fuenteTitulo.setFontName("Arial");
        fuenteTitulo.setColor(IndexedColors.DARK_BLUE.getIndex());
        fuenteGris.setFontName("Arial");
        fuenteGris.setFontHeightInPoints((short) 10);
        fuenteBlanca.setFontName("Arial");
        fuenteBlanca.setFontHeightInPoints((short) 10);
        ((XSSFCellStyle) estiloEncabezado).setFillForegroundColor(color("15364F"));
        estiloSubtitulo.setWrapText(true);
        for (Tipo tipo : Tipo.values()) {
            CellStyle base = switch (tipo) {
                case ENTERO -> estiloEntero;
                case MONEDA -> estiloMoneda;
                case FECHA -> estiloFecha;
                case TIPO_CAMBIO -> estiloTipoCambio;
                default -> libro.createCellStyle();
            };
            for (int banda = 0; banda < 2; banda++) {
                XSSFCellStyle estilo = (XSSFCellStyle) libro.createCellStyle();
                estilo.cloneStyleFrom(base);
                Font fuente = libro.createFont();
                fuente.setFontName("Arial");
                fuente.setFontHeightInPoints((short) 10);
                fuente.setColor(IndexedColors.GREY_80_PERCENT.getIndex());
                estilo.setFont(fuente);
                estilo.setVerticalAlignment(VerticalAlignment.CENTER);
                estilo.setAlignment(tipo == Tipo.TEXTO ? HorizontalAlignment.LEFT
                        : tipo == Tipo.FECHA ? HorizontalAlignment.CENTER : HorizontalAlignment.RIGHT);
                estilo.setWrapText(tipo == Tipo.TEXTO);
                estilo.setFillForegroundColor(color(banda == 0 ? "FFFFFF" : "EFF4F8"));
                estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                estilosDatos[banda][tipo.ordinal()] = estilo;
            }
            XSSFCellStyle total = (XSSFCellStyle) libro.createCellStyle();
            total.cloneStyleFrom(estilosDatos[0][tipo.ordinal()]);
            Font fuente = libro.createFont();
            fuente.setFontName("Arial");
            fuente.setFontHeightInPoints((short) 10);
            fuente.setBold(true);
            fuente.setColor(IndexedColors.DARK_BLUE.getIndex());
            total.setFont(fuente);
            total.setFillForegroundColor(color("DDE9F2"));
            total.setBorderTop(BorderStyle.MEDIUM);
            total.setTopBorderColor(IndexedColors.DARK_BLUE.getIndex());
            estilosTotales[tipo.ordinal()] = total;
        }
        try (InputStream imagen = ExcelBuilder.class.getResourceAsStream("/static/images/logo.png")) {
            if (imagen != null) logo = libro.addPicture(imagen.readAllBytes(), Workbook.PICTURE_TYPE_PNG);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el logo de JD", e);
        }
    }

    private static XSSFColor color(String hex) {
        return new XSSFColor(java.util.HexFormat.of().parseHex(hex), new DefaultIndexedColorMap());
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
        private final List<String> notas = new ArrayList<>();

        private Hoja(Sheet hoja, String titulo, String subtitulo, Columna[] columnas) {
            this.hoja = hoja;
            this.columnas = columnas;

            hoja.setDisplayGridlines(false);
            hoja.setPrintGridlines(false);
            hoja.setDefaultRowHeightInPoints(25);
            ((XSSFSheet) hoja).setTabColor(color("15364F"));
            hoja.createRow(0).setHeightInPoints(30);
            hoja.createRow(1).setHeightInPoints(20);
            hoja.createRow(2).setHeightInPoints(16);
            Row tituloFila = hoja.createRow(3);
            tituloFila.setHeightInPoints(27);
            Cell celdaTitulo = tituloFila.createCell(0);
            celdaTitulo.setCellValue(titulo);
            celdaTitulo.setCellStyle(estiloTitulo);

            Row subFila = hoja.createRow(4);
            subFila.setHeightInPoints(34);
            Cell celdaSub = subFila.createCell(0);
            celdaSub.setCellValue("JD Refrigeración S.A.C. · " + subtitulo
                    + " · generado el " + LocalDateTime.now().format(GENERADO));
            celdaSub.setCellStyle(estiloSubtitulo);
            if (columnas.length > 1) {
                hoja.addMergedRegion(new CellRangeAddress(3, 3, 0, columnas.length - 1));
                hoja.addMergedRegion(new CellRangeAddress(4, 4, 0, columnas.length - 1));
            }

            Row encabezado = hoja.createRow(FILA_ENCABEZADO);
            encabezado.setHeightInPoints(30);
            for (int i = 0; i < columnas.length; i++) {
                Cell c = encabezado.createCell(i);
                c.setCellValue(columnas[i].titulo());
                c.setCellStyle(estiloEncabezado);
            }
            hoja.createFreezePane(columnas.length > 6 ? 1 : 0, FILA_ENCABEZADO + 1);
            hoja.setRepeatingRows(new CellRangeAddress(FILA_ENCABEZADO, FILA_ENCABEZADO, -1, -1));
            hoja.setFitToPage(true);
            PrintSetup impresion = hoja.getPrintSetup();
            impresion.setLandscape(columnas.length > 6);
            impresion.setPaperSize(columnas.length > 12 ? PrintSetup.A3_PAPERSIZE : PrintSetup.A4_PAPERSIZE);
            impresion.setFitWidth((short) 1);
            impresion.setFitHeight((short) 0);
            hoja.setMargin(Sheet.LeftMargin, 0.3);
            hoja.setMargin(Sheet.RightMargin, 0.3);
            hoja.getFooter().setLeft("JD Refrigeración S.A.C.");
            hoja.getFooter().setRight("Página &P de &N");
        }

        /** Agrega una fila; cada valor se formatea según el tipo de su columna. null = celda vacía. */
        public Hoja fila(Object... valores) {
            Row fila = hoja.createRow(siguienteFila++);
            for (int i = 0; i < columnas.length; i++) {
                escribir(fila.createCell(i), i < valores.length ? valores[i] : null, columnas[i].tipo(), false);
            }
            return this;
        }

        /** Fila de totales en negrita (sin autofiltro). */
        public Hoja filaTotal(Object... valores) {
            // Deja una fila en blanco entre los datos y el primer total
            Row fila = hoja.createRow(siguienteFila + 1 + filasTotal++);
            fila.setHeightInPoints(42);
            for (int i = 0; i < columnas.length; i++) {
                escribir(fila.createCell(i), i < valores.length ? valores[i] : null, columnas[i].tipo(), true);
            }
            return this;
        }

        public int cantidadFilas() {
            return siguienteFila - FILA_ENCABEZADO - 1;
        }

        /** Observaciones fuera del rango filtrable y sin alterar los registros del reporte. */
        public Hoja nota(String texto) {
            notas.add(texto);
            return this;
        }

        private void escribir(Cell celda, Object valor, Tipo tipo, boolean total) {
            Tipo formato = valor != null && !(valor instanceof Number) && !(valor instanceof LocalDate)
                    ? Tipo.TEXTO : tipo;
            celda.setCellStyle(total ? estilosTotales[formato.ordinal()]
                    : estilosDatos[(celda.getRowIndex() - FILA_ENCABEZADO - 1) % 2][formato.ordinal()]);
            if (valor == null) {
                return;
            }
            if (valor instanceof LocalDate d) {
                celda.setCellValue(d);
            } else if (valor instanceof Number n && tipo != Tipo.TEXTO) {
                celda.setCellValue(n instanceof BigDecimal b ? b.doubleValue() : n.doubleValue());
            } else if (valor instanceof Boolean b) {
                celda.setCellValue(b);
            } else {
                celda.setCellValue(String.valueOf(valor));
            }
        }

        private void cerrar() {
            if (cantidadFilas() > 0) {
                hoja.setAutoFilter(new CellRangeAddress(FILA_ENCABEZADO, siguienteFila - 1, 0, columnas.length - 1));
            }
            for (int i = 0; i < columnas.length; i++) {
                hoja.autoSizeColumn(i);
                // Límites para que un nombre muy largo no deje una columna inmanejable
                int ancho = Math.max(hoja.getColumnWidth(i), (columnas[i].tipo() == Tipo.MONEDA ? 16 : 14) * 256);
                hoja.setColumnWidth(i, Math.min(ancho + 512, 60 * 256));
            }
            if (logo >= 0) {
                ClientAnchor ancla = libro.getCreationHelper().createClientAnchor();
                ancla.setCol1(0);
                ancla.setRow1(0);
                ancla.setAnchorType(ClientAnchor.AnchorType.MOVE_DONT_RESIZE);
                // El PNG corporativo no declara una resolución utilizable por resize().
                // Fijar sus dimensiones conserva la proporción y evita imágenes de tamaño cero.
                double restante = 230;
                int columnaFinal = 0;
                while (restante > hoja.getColumnWidthInPixels(columnaFinal)) {
                    restante -= hoja.getColumnWidthInPixels(columnaFinal++);
                }
                ancla.setCol2(columnaFinal);
                ancla.setDx2(org.apache.poi.util.Units.pixelToEMU((int)restante));
                ancla.setRow2(2);
                ancla.setDy2(org.apache.poi.util.Units.pixelToEMU(10));
                hoja.createDrawingPatriarch().createPicture(ancla, logo);
            }
            // Ajustar las filas descriptivas al ancho final, también en nombres largos.
            for (Row fila : hoja) {
                if (fila.getRowNum() <= FILA_ENCABEZADO) continue;
                int lineas = 1;
                for (Cell celda : fila) {
                    if (celda.getCellType() == CellType.STRING) {
                        int capacidad = Math.max(1, hoja.getColumnWidth(celda.getColumnIndex()) / 256 - 2);
                        lineas = Math.max(lineas, (int) Math.ceil((double) celda.getStringCellValue().length() / capacidad));
                    }
                }
                fila.setHeightInPoints(Math.max(fila.getHeightInPoints(), 15 * lineas + 10));
            }
            for (String nota : notas) {
                Row fila = hoja.createRow(hoja.getLastRowNum() + 2);
                Cell celda = fila.createCell(0);
                celda.setCellValue(nota);
                CellStyle estiloNota = libro.createCellStyle();
                estiloNota.cloneStyleFrom(estiloSubtitulo);
                estiloNota.setWrapText(true);
                celda.setCellStyle(estiloNota);
                if (columnas.length > 1) hoja.addMergedRegion(new CellRangeAddress(fila.getRowNum(), fila.getRowNum(), 0, columnas.length - 1));
                int capacidad = 0;
                for (int i = 0; i < columnas.length; i++) capacidad += hoja.getColumnWidth(i) / 256;
                fila.setHeightInPoints(15 * (float)Math.ceil((double)nota.length() / Math.max(1, capacidad - 2)) + 10);
            }
            libro.setPrintArea(libro.getSheetIndex(hoja), 0, columnas.length - 1, 0, hoja.getLastRowNum());
        }
    }
}
