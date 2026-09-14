package com.jdrefrigeracion.cotizaciones.services;

import com.jdrefrigeracion.cotizaciones.models.Cotizacion;
import com.jdrefrigeracion.cotizaciones.models.ItemCotizacion;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;

@Service
public class GeneradorPdfService {

    public File generarCotizacionPdf(Cotizacion cotizacion) throws Exception {
        // Archivo temporal
        File pdfFile = File.createTempFile("Cotizacion_" + cotizacion.getId() + "_", ".pdf");
        
        try (Document document = new Document(PageSize.A4)) {
            PdfWriter.getInstance(document, new FileOutputStream(pdfFile));
            document.open();

            // Tipografías
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, Color.BLACK);
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.DARK_GRAY);
            Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.BLACK);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 12, Color.BLACK);
            Font smallFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.GRAY);

            // Agregar Logo (desde resources si es posible, o usar texto si falla)
            try {
                // OpenPDF permite cargar imágenes locales o del classpath si las leemos como byte[] o URL
                // Por simplicidad, leeremos la imagen estática directamente. Como el contexto es local:
                Image logo = Image.getInstance("src/main/resources/static/images/logo.png");
                logo.scaleToFit(150, 150);
                logo.setAlignment(Element.ALIGN_RIGHT);
                document.add(logo);
            } catch (Exception e) {
                // Fallback si no encuentra el logo
                Paragraph fallback = new Paragraph("JD REFRIGERACION S.A.C.", titleFont);
                fallback.setAlignment(Element.ALIGN_RIGHT);
                document.add(fallback);
            }

            // Título
            Paragraph titulo = new Paragraph("COTIZACIÓN COMERCIAL", titleFont);
            titulo.setSpacingAfter(10f);
            document.add(titulo);
            
            // Línea separadora
            document.add(new Chunk("______________________________________________________________________________\n", smallFont));
            
            // Datos generales
            document.add(new Paragraph("Nº de Cotización: " + cotizacion.getId(), boldFont));
            document.add(new Paragraph("Fecha de Emisión: " + cotizacion.getFechaEmision(), normalFont));
            document.add(new Paragraph("Válido hasta: " + cotizacion.getFechaVencimiento(), normalFont));
            document.add(new Paragraph(" "));
            
            // Datos del cliente
            document.add(new Paragraph("Preparado para:", headerFont));
            document.add(new Paragraph(cotizacion.getCliente().getNombreORazonSocial(), boldFont));
            document.add(new Paragraph("RUC: " + cotizacion.getCliente().getRuc(), normalFont));
            if (cotizacion.getCliente().getDireccion() != null) {
                document.add(new Paragraph(cotizacion.getCliente().getDireccion(), normalFont));
            }
            document.add(new Paragraph(" "));
            document.add(new Paragraph(" "));
            
            // Tabla de productos
            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{4f, 1f, 1.5f, 1.5f});
            
            // Cabeceras de tabla
            agregarCeldaCabecera(table, "Descripción", headerFont);
            agregarCeldaCabecera(table, "Cant.", headerFont);
            agregarCeldaCabecera(table, "P. Unit", headerFont);
            agregarCeldaCabecera(table, "Subtotal", headerFont);
            
            // Items
            for (ItemCotizacion item : cotizacion.getItems()) {
                agregarCeldaItem(table, item.getProducto().getNombre() + "\n" + item.getProducto().getDescripcion(), normalFont, Element.ALIGN_LEFT);
                agregarCeldaItem(table, String.valueOf(item.getCantidad()), normalFont, Element.ALIGN_CENTER);
                agregarCeldaItem(table, cotizacion.getMoneda() + " " + item.getPrecioUnitario().setScale(2, java.math.RoundingMode.HALF_UP), normalFont, Element.ALIGN_RIGHT);
                agregarCeldaItem(table, cotizacion.getMoneda() + " " + item.calcularSubtotalItem(), normalFont, Element.ALIGN_RIGHT);
            }
            document.add(table);
            
            // Tabla de Totales
            document.add(new Paragraph(" "));
            PdfPTable totalesTable = new PdfPTable(2);
            totalesTable.setWidthPercentage(40);
            totalesTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalesTable.setWidths(new float[]{1f, 1f});
            
            agregarCeldaTotal(totalesTable, "Subtotal:", normalFont);
            agregarCeldaTotal(totalesTable, cotizacion.getMoneda() + " " + cotizacion.calcularSubtotal(), normalFont);
            
            agregarCeldaTotal(totalesTable, "IGV (18%):", normalFont);
            agregarCeldaTotal(totalesTable, cotizacion.getMoneda() + " " + cotizacion.calcularIgv(), normalFont);
            
            agregarCeldaTotal(totalesTable, "TOTAL:", boldFont);
            agregarCeldaTotal(totalesTable, cotizacion.getMoneda() + " " + cotizacion.calcularTotal(), boldFont);
            
            document.add(totalesTable);
            
            // Notas finales
            document.add(new Paragraph(" "));
            document.add(new Paragraph(" "));
            Paragraph notas = new Paragraph("Términos y condiciones:\n- Los precios incluyen impuestos según se detalla.\n- Esta cotización es válida por el período indicado.\n- Para confirmar la orden, por favor envíe su orden de compra aprobada.", smallFont);
            document.add(notas);
            
        } catch (Exception e) {
            throw new RuntimeException("Error al generar el PDF de la cotización", e);
        }
        
        return pdfFile;
    }
    
    private void agregarCeldaCabecera(PdfPTable table, String texto, Font font) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, font));
        celda.setBackgroundColor(new Color(230, 230, 230));
        celda.setPadding(8f);
        celda.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(celda);
    }
    
    private void agregarCeldaItem(PdfPTable table, String texto, Font font, int alineacion) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, font));
        celda.setPadding(8f);
        celda.setHorizontalAlignment(alineacion);
        table.addCell(celda);
    }
    
    private void agregarCeldaTotal(PdfPTable table, String texto, Font font) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, font));
        celda.setBorder(Rectangle.NO_BORDER);
        celda.setPadding(5f);
        celda.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(celda);
    }
}
