package com.bomerp.proformas.pdf.service;

import com.bomerp.config.empresa.ConfiguracionEmpresaService;
import com.bomerp.config.empresa.EmpresaProperties;
import com.bomerp.proformas.proforma.entity.Proforma;
import com.bomerp.proformas.itemproforma.entity.ItemProforma;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;

@Service
public class GeneradorPdfService {

    private final EmpresaProperties empresa;
    private final ConfiguracionEmpresaService configuracionEmpresaService;

    @Autowired
    public GeneradorPdfService(EmpresaProperties empresa, ConfiguracionEmpresaService configuracionEmpresaService) {
        this.empresa = empresa;
        this.configuracionEmpresaService = configuracionEmpresaService;
    }

    public GeneradorPdfService(EmpresaProperties empresa) {
        this(empresa, null);
    }

    /**
     * PDF de la proforma en memoria (no deja archivos temporales). Lo usan el correo y la vista previa.
     * Lee el cliente y los ítems, así que se llama dentro de una transacción.
     *
     * @throws IllegalStateException si no se pudo armar el PDF
     */
    public byte[] generarProformaPdf(Proforma proforma) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (Document document = new Document(PageSize.A4)) {
            PdfWriter.getInstance(document, salida);
            document.open();

            // Tipografías
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, Color.BLACK);
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.DARK_GRAY);
            Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.BLACK);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 12, Color.BLACK);
            Font smallFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.GRAY);

            // Agregar Logo (desde resources si es posible, o usar texto si falla)
            try {
                // Desde el classpath: la ruta del disco no existe dentro de la imagen Docker de producción
                Image logo;
                try (java.io.InputStream in = GeneradorPdfService.class.getResourceAsStream("/static/images/logo.png")) {
                    if (in == null) throw new java.io.FileNotFoundException("static/images/logo.png");
                    logo = Image.getInstance(in.readAllBytes());
                }
                logo.scaleToFit(150, 150);
                logo.setAlignment(Element.ALIGN_RIGHT);
                document.add(logo);
            } catch (Exception e) {
                // Fallback si no encuentra el logo
                Paragraph fallback = new Paragraph(empresa.razonSocial(), titleFont);
                fallback.setAlignment(Element.ALIGN_RIGHT);
                document.add(fallback);
            }

            // Datos de JD (emisor)
            Paragraph emisor = new Paragraph(empresa.razonSocial() + " · RUC " + empresa.ruc() + "\n" + empresa.direccion(), smallFont);
            emisor.setAlignment(Element.ALIGN_RIGHT);
            emisor.setSpacingAfter(10f);
            document.add(emisor);

            // Título
            Paragraph titulo = new Paragraph("PROFORMA COMERCIAL", titleFont);
            titulo.setSpacingAfter(10f);
            document.add(titulo);
            
            // Línea separadora
            document.add(new Chunk("______________________________________________________________________________\n", smallFont));
            
            // Datos generales
            document.add(new Paragraph("Nº de Proforma: " + proforma.getId(), boldFont));
            document.add(new Paragraph("Fecha de Emisión: " + proforma.getFechaEmision(), normalFont));
            document.add(new Paragraph("Válido hasta: " + proforma.getFechaVencimiento(), normalFont));
            document.add(new Paragraph(" "));
            
            // Datos del cliente
            document.add(new Paragraph("Preparado para:", headerFont));
            document.add(new Paragraph(proforma.getCliente().getNombreORazonSocial(), boldFont));
            document.add(new Paragraph("RUC: " + proforma.getCliente().getRuc(), normalFont));
            if (proforma.getCliente().getDireccion() != null) {
                document.add(new Paragraph(proforma.getCliente().getDireccion(), normalFont));
            }
            document.add(new Paragraph(" "));
            document.add(new Paragraph(" "));
            
            // Tabla de productos
            PdfPTable table = new PdfPTable(5);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{4f, 0.8f, 1.5f, 0.9f, 1.5f});
            
            // Cabeceras de tabla
            agregarCeldaCabecera(table, "Descripción", headerFont);
            agregarCeldaCabecera(table, "Cant.", headerFont);
            agregarCeldaCabecera(table, "P. Unit", headerFont);
            agregarCeldaCabecera(table, "Dscto.", headerFont);
            agregarCeldaCabecera(table, "Subtotal", headerFont);
            
            // Items
            for (ItemProforma item : proforma.getItems()) {
                agregarCeldaItem(table, item.getProducto().getNombre()
                        + (item.getProducto().getDescripcion() == null ? "" : "\n" + item.getProducto().getDescripcion()), normalFont, Element.ALIGN_LEFT);
                agregarCeldaItem(table, String.valueOf(item.getCantidad()), normalFont, Element.ALIGN_CENTER);
                agregarCeldaItem(table, proforma.getMoneda() + " " + item.getPrecioUnitario().setScale(2, java.math.RoundingMode.HALF_UP), normalFont, Element.ALIGN_RIGHT);
                agregarCeldaItem(table, item.getDescuentoPorcentaje().signum() > 0
                        ? item.getDescuentoPorcentaje().stripTrailingZeros().toPlainString() + " %" : "—",
                        normalFont, Element.ALIGN_CENTER);
                agregarCeldaItem(table, proforma.getMoneda() + " " + item.calcularSubtotalItem(), normalFont, Element.ALIGN_RIGHT);
            }
            document.add(table);
            
            // Tabla de Totales
            document.add(new Paragraph(" "));
            PdfPTable totalesTable = new PdfPTable(2);
            totalesTable.setWidthPercentage(40);
            totalesTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalesTable.setWidths(new float[]{1f, 1f});
            
            agregarCeldaTotal(totalesTable, "Subtotal:", normalFont);
            agregarCeldaTotal(totalesTable, proforma.getMoneda() + " " + proforma.calcularSubtotal(), normalFont);
            
            agregarCeldaTotal(totalesTable, "IGV (18%):", normalFont);
            agregarCeldaTotal(totalesTable, proforma.getMoneda() + " " + proforma.calcularIgv(), normalFont);
            
            agregarCeldaTotal(totalesTable, "TOTAL:", boldFont);
            agregarCeldaTotal(totalesTable, proforma.getMoneda() + " " + proforma.calcularTotal(), boldFont);
            
            document.add(totalesTable);
            
            // Notas finales y Cuentas Bancarias
            document.add(new Paragraph(" "));
            Paragraph notas = new Paragraph("Términos y condiciones:\n- Los precios incluyen impuestos según se detalla.\n- Esta proforma es válida por el período indicado.\n- Para confirmar la orden, por favor envíe su orden de compra aprobada.", smallFont);
            notas.setSpacingAfter(8f);
            document.add(notas);

            if (proforma.getCondicionesPago() != null && !proforma.getCondicionesPago().isBlank()) {
                Paragraph condiciones = new Paragraph("Condiciones de pago", headerFont);
                condiciones.setKeepTogether(true);
                condiciones.add(new Chunk("\n" + proforma.getCondicionesPago(), normalFont));
                condiciones.setSpacingAfter(10f);
                document.add(condiciones);
            }

            // Cuentas bancarias para depósitos
            String ctaBcp = empresa.cuentaBcp();
            String cciBcp = empresa.cciBcp();
            String ctaBn = empresa.cuentaNacion();
            if (configuracionEmpresaService != null) {
                var conf = configuracionEmpresaService.obtener();
                ctaBcp = conf.cuentaBcp();
                cciBcp = conf.cciBcp();
                ctaBn = conf.cuentaNacion();
            }

            Font bancoHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.DARK_GRAY);
            Font bancoFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);

            PdfPTable tablaBancos = new PdfPTable(1);
            tablaBancos.setWidthPercentage(100);
            PdfPCell celdaBanco = new PdfPCell();
            celdaBanco.setBackgroundColor(new Color(248, 250, 252));
            celdaBanco.setBorderColor(new Color(226, 232, 240));
            celdaBanco.setPadding(8f);

            Paragraph tituloBancos = new Paragraph("Cuentas bancarias para depósitos y transferencias:", bancoHeaderFont);
            tituloBancos.setSpacingAfter(4f);
            celdaBanco.addElement(tituloBancos);

            agregarCuenta(celdaBanco, "BCP - cuenta en soles", ctaBcp, bancoFont);
            agregarCuenta(celdaBanco, "BCP - CCI", cciBcp, bancoFont);
            agregarCuenta(celdaBanco, "Banco de la Nación - cuenta", ctaBn, bancoFont);
            agregarCuenta(celdaBanco, "Banco de la Nación - cuenta de detracciones", empresa.cuentaDetracciones(), bancoFont);

            if (celdaBanco.getCompositeElements().size() > 1) {
                tablaBancos.addCell(celdaBanco);
                document.add(tablaBancos);
            }
            
        } catch (Exception e) {
            throw new IllegalStateException("Error al generar el PDF de la proforma #" + proforma.getId(), e);
        }
        return salida.toByteArray();
    }
    
    private void agregarCuenta(PdfPCell celda, String etiqueta, String cuenta, Font fuente) {
        if (cuenta != null && !cuenta.isBlank()) {
            celda.addElement(new Paragraph(etiqueta + ": " + cuenta.trim(), fuente));
        }
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
