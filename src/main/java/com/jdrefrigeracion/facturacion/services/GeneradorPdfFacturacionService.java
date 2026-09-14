package com.jdrefrigeracion.facturacion.services;

import com.jdrefrigeracion.facturacion.models.Comprobante;
import com.jdrefrigeracion.ventas.models.DetalleVenta;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;

@Service
public class GeneradorPdfFacturacionService {

    public File generarFacturaPdf(Comprobante comprobante) throws Exception {
        String fileName = comprobante.getTipo().name() + "_" + comprobante.getSerie() + "-" + comprobante.getNumero() + ".pdf";
        File pdfFile = File.createTempFile("Comprobante_", "_" + fileName);
        
        try (Document document = new Document(PageSize.A4)) {
            PdfWriter.getInstance(document, new FileOutputStream(pdfFile));
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.BLACK);
            Font redTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.RED);
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.DARK_GRAY);
            Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.BLACK);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 12, Color.BLACK);
            Font smallFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.GRAY);

            // Tabla principal para el Header (Logo Izquierda, Cuadro RUC Derecha)
            PdfPTable headerTable = new PdfPTable(2);
            headerTable.setWidthPercentage(100);
            headerTable.setWidths(new float[]{2f, 1.2f});
            
            // Celda Izquierda: Logo y Empresa
            PdfPCell leftCell = new PdfPCell();
            leftCell.setBorder(Rectangle.NO_BORDER);
            try {
                Image logo = Image.getInstance("src/main/resources/static/images/logo.png");
                logo.scaleToFit(140, 140);
                logo.setAlignment(Element.ALIGN_LEFT);
                leftCell.addElement(logo);
            } catch (Exception e) {
                leftCell.addElement(new Paragraph("JD REFRIGERACION S.A.C.", titleFont));
            }
            leftCell.addElement(new Paragraph("RUC: 20123456789", boldFont));
            leftCell.addElement(new Paragraph("Av. Las Refrigeradoras 123, Lima", normalFont));
            leftCell.addElement(new Paragraph("Teléfono: (01) 555-1234", normalFont));
            headerTable.addCell(leftCell);
            
            // Celda Derecha: Cuadro RUC y Número de Factura
            PdfPCell rightCell = new PdfPCell();
            rightCell.setBorder(Rectangle.BOX);
            rightCell.setBorderWidth(1.5f);
            rightCell.setPadding(10f);
            rightCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            rightCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            
            Paragraph rucEmpresa = new Paragraph("RUC: 20123456789", boldFont);
            rucEmpresa.setAlignment(Element.ALIGN_CENTER);
            rightCell.addElement(rucEmpresa);
            
            String tipoTexto = comprobante.getTipo() == Comprobante.TipoComprobante.FACTURA ? "FACTURA ELECTRÓNICA" : "BOLETA ELECTRÓNICA";
            Paragraph tipoComp = new Paragraph(tipoTexto, titleFont);
            tipoComp.setAlignment(Element.ALIGN_CENTER);
            rightCell.addElement(tipoComp);
            
            Paragraph numComp = new Paragraph(comprobante.getSerie() + "-" + comprobante.getNumero(), redTitleFont);
            numComp.setAlignment(Element.ALIGN_CENTER);
            rightCell.addElement(numComp);
            
            headerTable.addCell(rightCell);
            document.add(headerTable);
            
            document.add(new Paragraph(" "));
            
            // Datos del Cliente
            document.add(new Paragraph("SEÑOR(ES): " + comprobante.getVenta().getCliente().getNombreORazonSocial(), boldFont));
            if (comprobante.getVenta().getCliente().getRuc() != null) {
                document.add(new Paragraph("RUC: " + comprobante.getVenta().getCliente().getRuc(), normalFont));
            } else if (comprobante.getVenta().getCliente().getDni() != null) {
                document.add(new Paragraph("DNI: " + comprobante.getVenta().getCliente().getDni(), normalFont));
            }
            if (comprobante.getVenta().getCliente().getDireccion() != null) {
                document.add(new Paragraph("DIRECCIÓN: " + comprobante.getVenta().getCliente().getDireccion(), normalFont));
            }
            document.add(new Paragraph("FECHA DE EMISIÓN: " + comprobante.getFechaEmision(), normalFont));
            document.add(new Paragraph("MONEDA: " + comprobante.getVenta().getMoneda(), normalFont));
            
            document.add(new Paragraph(" "));
            
            // Tabla de Detalles
            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{4f, 1f, 1.5f, 1.5f});
            
            agregarCeldaCabecera(table, "Descripción", headerFont);
            agregarCeldaCabecera(table, "Cant.", headerFont);
            agregarCeldaCabecera(table, "V. Unit", headerFont);
            agregarCeldaCabecera(table, "Subtotal", headerFont);
            
            for (DetalleVenta item : comprobante.getVenta().getDetalles()) {
                agregarCeldaItem(table, item.getProducto().getNombre() + "\n" + item.getProducto().getDescripcion(), normalFont, Element.ALIGN_LEFT);
                agregarCeldaItem(table, String.valueOf(item.getCantidad()), normalFont, Element.ALIGN_CENTER);
                agregarCeldaItem(table, comprobante.getVenta().getMoneda() + " " + item.getPrecioUnitario().setScale(2, java.math.RoundingMode.HALF_UP), normalFont, Element.ALIGN_RIGHT);
                agregarCeldaItem(table, comprobante.getVenta().getMoneda() + " " + item.calcularSubtotalDetalle().setScale(2, java.math.RoundingMode.HALF_UP), normalFont, Element.ALIGN_RIGHT);
            }
            document.add(table);
            
            // Tabla de Totales
            document.add(new Paragraph(" "));
            PdfPTable totalesTable = new PdfPTable(2);
            totalesTable.setWidthPercentage(40);
            totalesTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalesTable.setWidths(new float[]{1f, 1f});
            
            agregarCeldaTotal(totalesTable, "OP. GRAVADA:", normalFont);
            agregarCeldaTotal(totalesTable, comprobante.getVenta().getMoneda() + " " + comprobante.getVenta().calcularSubtotal(), normalFont);
            
            agregarCeldaTotal(totalesTable, "IGV (18%):", normalFont);
            agregarCeldaTotal(totalesTable, comprobante.getVenta().getMoneda() + " " + comprobante.getVenta().calcularIgv(), normalFont);
            
            agregarCeldaTotal(totalesTable, "IMPORTE TOTAL:", boldFont);
            agregarCeldaTotal(totalesTable, comprobante.getVenta().getMoneda() + " " + comprobante.getVenta().calcularTotal(), boldFont);
            
            document.add(totalesTable);
            
            // Texto final
            document.add(new Paragraph(" "));
            document.add(new Paragraph("Representación impresa de la " + tipoTexto + ".", smallFont));
            document.add(new Paragraph("Consulte su documento electrónico en nuestro portal.", smallFont));
            
        } catch (Exception e) {
            throw new RuntimeException("Error al generar el PDF de facturación", e);
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
