package com.bomerp.facturacion.pdf.service;

import com.bomerp.config.empresa.EmpresaProperties;
import com.bomerp.facturacion.comprobante.entity.Comprobante;
import com.bomerp.ventas.detalleventa.entity.DetalleVenta;
import com.bomerp.ventas.venta.entity.Venta;
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

    private final EmpresaProperties empresa;

    public GeneradorPdfFacturacionService(EmpresaProperties empresa) {
        this.empresa = empresa;
    }

    /** El PDF en memoria (para adjuntarlo al correo o descargarlo), sin dejar archivos temporales. */
    public byte[] generarFacturaPdfBytes(Comprobante comprobante) throws Exception {
        File archivo = generarFacturaPdf(comprobante);
        try {
            return java.nio.file.Files.readAllBytes(archivo.toPath());
        } finally {
            archivo.delete();
        }
    }

    public File generarFacturaPdf(Comprobante comprobante) throws Exception {
        String fileName = comprobante.getTipo().name() + "_" + comprobante.getSerie() + "-" + comprobante.getNumero() + ".pdf";
        File pdfFile = File.createTempFile("Comprobante_", "_" + fileName);
        
        try (Document document = new Document(PageSize.A4, 42, 42, 32, 52)) {
            PdfEditorial.preparar(PdfWriter.getInstance(document, new FileOutputStream(pdfFile)));
            document.open();

            Font redTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, new Color(145, 85, 32));
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.DARK_GRAY);
            Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, PdfEditorial.TINTA);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 9.5f, PdfEditorial.TINTA);
            Font smallFont = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(70, 85, 95));

            String tipoTexto = comprobante.getTipo() == Comprobante.TipoComprobante.FACTURA ? "FACTURA ELECTRÓNICA" : "BOLETA ELECTRÓNICA";
            PdfEditorial.encabezado(document, empresa, tipoTexto, comprobante.getSerie() + "-" + comprobante.getNumero());
            if (comprobante.getAmbienteEmision() == Comprobante.AmbienteEmision.SIMULADO
                    || comprobante.getAmbienteEmision() == Comprobante.AmbienteEmision.BETA) {
                document.add(new Paragraph("DOCUMENTO DE PRUEBA - SIN VALIDEZ TRIBUTARIA", redTitleFont));
            }
            
            espacio(document);
            
            // Datos del Cliente
            // El receptor tal como se emitió el comprobante: debe coincidir con el XML que recibió SUNAT, aunque
            // el cliente se haya editado después. Sin datos guardados (no debería haber), los del cliente actual.
            boolean guardado = comprobante.getReceptorNumDoc() != null;
            var cliente = comprobante.getVenta().getCliente();
            String nombreReceptor = guardado ? comprobante.getReceptorNombre() : cliente.getNombreORazonSocial();
            String ruc = guardado ? ("6".equals(comprobante.getReceptorTipoDoc()) ? comprobante.getReceptorNumDoc() : null) : cliente.getRuc();
            String dni = guardado ? ("1".equals(comprobante.getReceptorTipoDoc()) ? comprobante.getReceptorNumDoc() : null) : cliente.getDni();
            String direccion = guardado ? comprobante.getReceptorDireccion() : cliente.getDireccion();
            PdfPTable receptor = new PdfPTable(new float[]{2,1}); receptor.setWidthPercentage(100);
            PdfPCell datos = new PdfPCell(); datos.setBorder(Rectangle.NO_BORDER); datos.setPaddingLeft(0);
            datos.addElement(new Paragraph("SEÑOR(ES): " + nombreReceptor, boldFont));
            if(ruc != null) datos.addElement(new Paragraph("RUC: " + ruc, normalFont));
            else if(dni != null) datos.addElement(new Paragraph("DNI: " + dni, normalFont));
            if(direccion != null) datos.addElement(new Paragraph("DIRECCIÓN: " + direccion, normalFont));
            receptor.addCell(datos);
            PdfPCell fechas = new PdfPCell(); fechas.setBorder(Rectangle.NO_BORDER); fechas.setPaddingLeft(10);
            fechas.addElement(new Paragraph("FECHA DE EMISIÓN: " + comprobante.getFechaEmision(), normalFont));
            fechas.addElement(new Paragraph("MONEDA: " + (comprobante.getVenta().getMoneda() == Venta.Moneda.DOLARES ? "DÓLARES (USD)" : "SOLES (PEN)"), normalFont));
            if(comprobante.getVenta().getMoneda() == Venta.Moneda.DOLARES && comprobante.getVenta().getTipoCambio() != null)
                fechas.addElement(new Paragraph("TIPO DE CAMBIO: S/ " + comprobante.getVenta().getTipoCambio().setScale(3, java.math.RoundingMode.HALF_UP) + " por US$ 1", normalFont));
            receptor.addCell(fechas); document.add(receptor);
            espacio(document);
            agregarCondicionesPago(document, comprobante.getVenta(), boldFont, normalFont, smallFont);

            // Tabla de Detalles
            PdfPTable table = new PdfPTable(5);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{4f, 0.8f, 1.5f, 0.9f, 1.5f});
            table.setHeaderRows(1);
            table.setSpacingBefore(8);

            agregarCeldaCabecera(table, "Descripción", headerFont);
            agregarCeldaCabecera(table, "Cant.", headerFont);
            agregarCeldaCabecera(table, "V. Unit", headerFont);
            agregarCeldaCabecera(table, "Dscto.", headerFont);
            agregarCeldaCabecera(table, "Subtotal", headerFont);
            
            for (DetalleVenta item : comprobante.getVenta().getDetalles()) {
                // El nombre guardado al vender, el mismo que va en el XML (renombrar el producto no lo cambia)
                agregarCeldaItem(table, item.getDescripcion(), normalFont, Element.ALIGN_LEFT);
                agregarCeldaItem(table, String.valueOf(item.getCantidad()), normalFont, Element.ALIGN_CENTER);
                agregarCeldaItem(table, dinero(comprobante.getVenta(), item.getPrecioUnitario()), normalFont, Element.ALIGN_RIGHT);
                agregarCeldaItem(table, porcentaje(item.getDescuentoPorcentaje()), normalFont, Element.ALIGN_CENTER);
                agregarCeldaItem(table, dinero(comprobante.getVenta(), item.calcularSubtotalDetalle()), normalFont, Element.ALIGN_RIGHT);
            }
            document.add(table);
            
            // Tabla de Totales
            espacio(document);
            PdfPTable totalesTable = new PdfPTable(2);
            totalesTable.setWidthPercentage(65);
            totalesTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalesTable.setWidths(new float[]{3.4f, 2.1f});
            
            agregarCeldaTotal(totalesTable, "OP. GRAVADA:", normalFont);
            agregarCeldaTotal(totalesTable, dinero(comprobante.getVenta(), comprobante.getVenta().calcularSubtotal()), normalFont);
            
            agregarCeldaTotal(totalesTable, "IGV (18%):", normalFont);
            agregarCeldaTotal(totalesTable, dinero(comprobante.getVenta(), comprobante.getVenta().calcularIgv()), normalFont);
            
            agregarCeldaTotal(totalesTable, "IMPORTE TOTAL:", boldFont);
            agregarCeldaTotal(totalesTable, dinero(comprobante.getVenta(), comprobante.getVenta().calcularTotal()), boldFont);

            // Detracción (SPOT) o retención del IGV: lo que el cliente no le paga a JD directamente
            Venta venta = comprobante.getVenta();
            boolean conDeduccion = venta.getTipoDeduccion() != null && venta.getTipoDeduccion() != Venta.TipoDeduccion.NINGUNA;
            if (conDeduccion) {
                String nombre = venta.getTipoDeduccion() == Venta.TipoDeduccion.DETRACCION ? "DETRACCIÓN" : "RETENCIÓN PREVISTA";
                agregarCeldaTotal(totalesTable, nombre + " (" + porcentaje(venta.getPorcentajeDeduccion()) + "):", normalFont);
                agregarCeldaTotal(totalesTable, dinero(venta, venta.getMontoDeduccion()), normalFont);
                agregarCeldaTotal(totalesTable, venta.getTipoDeduccion() == Venta.TipoDeduccion.RETENCION
                        ? "NETO ESTIMADO:" : "PAGO DIRECTO A JD:", boldFont);
                agregarCeldaTotal(totalesTable, dinero(venta, venta.calcularNetoACobrar()), boldFont);
            }
            
            document.add(totalesTable);

            if (conDeduccion && venta.getTipoDeduccion() == Venta.TipoDeduccion.DETRACCION) {
                agregarDetraccion(document, comprobante, boldFont, smallFont);
            } else if (conDeduccion) {
                espacio(document);
                document.add(new Paragraph("Retención prevista del IGV (" + porcentaje(venta.getPorcentajeDeduccion())
                        + "): importe estimado para el pago total de " + venta.getMoneda() + " " + venta.getMontoDeduccion()
                        + ". Se calcula sobre cada pago y se acredita con el comprobante de retención del cliente. "
                        + "Información conservada del documento histórico.", smallFont));
            }
            if (empresa.cuentaBcp() != null && !empresa.cuentaBcp().isBlank()) {
                espacio(document);
                document.add(new Paragraph("Pago directo a JD - cuenta BCP en soles: " + empresa.cuentaBcp(), smallFont));
                if (empresa.cciBcp() != null && !empresa.cciBcp().isBlank()) {
                    document.add(new Paragraph("CCI BCP: " + empresa.cciBcp(), smallFont));
                }
            }

            // Texto final
            espacio(document);
            document.add(new Paragraph("Representación impresa de la " + tipoTexto + ".", smallFont));
            document.add(new Paragraph("Conserve el PDF, el XML y la constancia de detracción cuando corresponda.", smallFont));
            
        } catch (Exception e) {
            throw new RuntimeException("Error al generar el PDF de facturación", e);
        }
        
        return pdfFile;
    }
    
    private static void espacio(Document document) throws DocumentException {
        Paragraph espacio = new Paragraph(" "); espacio.setLeading(6); document.add(espacio);
    }

    private static String dinero(Venta venta, java.math.BigDecimal importe) {
        return (venta.getMoneda() == Venta.Moneda.DOLARES ? "US$ " : "S/ ")
                + importe.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private void agregarCondicionesPago(Document document, Venta venta, Font bold, Font normal, Font small) throws Exception {
        document.add(new Paragraph("CONDICIONES DE PAGO", bold));
        if (venta.getTipoPago() != Venta.TipoPago.CREDITO) {
            document.add(new Paragraph("Forma de pago: Contado", normal));
            espacio(document);
            return;
        }
        document.add(new Paragraph("Forma de pago: Crédito"
                + (venta.getDiasCredito() != null ? " - Plazo: " + venta.getDiasCredito() + " días" : ""), normal));
        document.add(new Paragraph("Monto neto del crédito: " + dinero(venta, venta.calcularNetoACobrar()), normal));
        document.add(new Paragraph("Cuota 001 - Vencimiento: "
                + (venta.getFechaLimitePago() != null
                    ? venta.getFechaLimitePago().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "por confirmar")
                + " - Importe: " + dinero(venta, venta.calcularNetoACobrar()), small));
        espacio(document);
    }

    private void agregarDetraccion(Document document, Comprobante comprobante, Font bold, Font small) throws Exception {
        Venta venta = comprobante.getVenta();
        String cuenta = comprobante.getCuentaDetraccionesEmision() != null ? comprobante.getCuentaDetraccionesEmision()
                : empresa.cuentaDetracciones();
        String codigo = venta.getCodigoDetraccion() != null ? venta.getCodigoDetraccion() : "020";
        String medio = comprobante.getMedioPagoDetraccion() != null ? comprobante.getMedioPagoDetraccion() : "001";
        PdfPTable bloque = new PdfPTable(1);
        bloque.setWidthPercentage(100);
        bloque.setSpacingBefore(12);
        PdfPCell celda = new PdfPCell();
        celda.setPadding(10);
        celda.setBorder(Rectangle.TOP);
        celda.setBorderColor(PdfEditorial.LINEA);
        celda.setBackgroundColor(PdfEditorial.SUAVE);
        celda.addElement(new Paragraph("OPERACIÓN SUJETA AL SPOT - DETRACCIÓN", bold));
        celda.addElement(new Paragraph("Tipo de operación: 1001 - Operación sujeta al Sistema de Pago de Obligaciones Tributarias", small));
        celda.addElement(new Paragraph("Código de bien/servicio: " + codigo + " - Porcentaje: "
                + porcentaje(venta.getPorcentajeDeduccion()), small));
        celda.addElement(new Paragraph("Cuenta de detracciones del Banco de la Nación: "
                + (cuenta != null && !cuenta.isBlank() ? cuenta : "por confirmar"), small));
        celda.addElement(new Paragraph("Medio de pago: " + medio + switch (medio) {
            case "001" -> " - Depósito en cuenta";
            case "003" -> " - Transferencia de fondos";
            default -> "";
        }, small));
        celda.addElement(new Paragraph("Monto a depositar en soles: S/ " + venta.detraccionEnSoles().setScale(2).toPlainString(), bold));
        celda.addElement(new Paragraph("Importe de la operación: " + dinero(venta, venta.calcularTotal()), small));
        if (venta.getMoneda() == Venta.Moneda.DOLARES) {
            celda.addElement(new Paragraph("Equivalente en la moneda del comprobante: " + dinero(venta, venta.getMontoDeduccion())
                    + " - Tipo de cambio utilizado: S/ " + venta.getTipoCambio(), small));
        }
        celda.addElement(new Paragraph("Pago directo a JD: " + dinero(venta, venta.calcularNetoACobrar()), bold));
        celda.addElement(new Paragraph("El depósito en el Banco de la Nación y el pago directo a JD son partes del importe total; "
                + "la constancia se registra por separado en Cobranza.", small));
        bloque.addCell(celda);
        document.add(bloque);
    }

    private static String porcentaje(java.math.BigDecimal descuento) {
        return descuento != null && descuento.signum() > 0 ? descuento.stripTrailingZeros().toPlainString() + " %" : "—";
    }

    private void agregarCeldaCabecera(PdfPTable table, String texto, Font font) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, font));
        celda.setBorder(Rectangle.TOP | Rectangle.BOTTOM);
        celda.setBorderColor(PdfEditorial.TINTA);
        celda.setPadding(8f);
        celda.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(celda);
    }
    
    private void agregarCeldaItem(PdfPTable table, String texto, Font font, int alineacion) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, font));
        celda.setBorder(Rectangle.BOTTOM);
        celda.setBorderColor(PdfEditorial.LINEA);
        celda.setPadding(9f);
        celda.setHorizontalAlignment(alineacion);
        table.addCell(celda);
    }
    
    private void agregarCeldaTotal(PdfPTable table, String texto, Font font) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, font));
        celda.setBorder(Rectangle.NO_BORDER);
        celda.setPadding(3f);
        celda.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(celda);
    }
}
