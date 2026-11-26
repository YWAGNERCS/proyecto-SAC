package com.bomerp.facturacion.pdf.service;

import com.bomerp.config.empresa.EmpresaProperties;
import com.bomerp.config.RecursoNoEncontradoException;
import com.bomerp.facturacion.comprobante.entity.Comprobante;
import com.bomerp.facturacion.notacredito.entity.NotaCredito;
import com.bomerp.facturacion.notacredito.repository.NotaCreditoRepository;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.ByteArrayOutputStream;
import java.awt.Color;
import java.math.RoundingMode;

@Service
public class NotaCreditoPdfService {
    private final NotaCreditoRepository notas;
    private final EmpresaProperties empresa;
    public NotaCreditoPdfService(NotaCreditoRepository notas, EmpresaProperties empresa) {
        this.notas = notas; this.empresa = empresa;
    }
    @Transactional(readOnly = true)
    public byte[] descargar(Long id) {
        NotaCredito n = notas.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Nota de crédito no encontrada"));
        return generar(n);
    }
    public byte[] generar(NotaCredito n) {
        var c = n.getComprobanteReferencia();
        var v = c.getVenta();
        var out = new ByteArrayOutputStream();
        Font normal = FontFactory.getFont(FontFactory.HELVETICA, 10);
        Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
        Font titulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 19, new Color(23,58,79));
        try (Document d = new Document(PageSize.A4, 38, 38, 32, 32)) {
            PdfWriter.getInstance(d, out); d.open();
            d.add(new Paragraph(empresa.razonSocial(), bold));
            d.add(new Paragraph("RUC: " + empresa.ruc() + " | " + empresa.direccion(), normal));
            Paragraph t = new Paragraph("NOTA DE CRÉDITO ELECTRÓNICA", titulo); t.setSpacingBefore(20); t.setSpacingAfter(10); d.add(t);
            d.add(new Paragraph(n.getSerie()+"-"+n.getNumero(), titulo));
            d.add(new Paragraph("Fecha de emisión: " + n.getFechaEmision(), normal));
            d.add(new Paragraph("Estado SUNAT: " + n.getEstadoSunat(), bold));
            if (!n.getEstadoSunat().valido() || n.getAmbienteEmision() != Comprobante.AmbienteEmision.PRODUCCION) {
                Paragraph aviso = new Paragraph("SIN VALIDEZ TRIBUTARIA ACREDITADA - "
                        + (n.getAmbienteEmision() == null ? "AMBIENTE NO REGISTRADO" : n.getAmbienteEmision()),
                        FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.RED));
                aviso.setSpacingBefore(10); aviso.setSpacingAfter(10); d.add(aviso);
            }
            d.add(new Paragraph("Cliente: " + (c.getReceptorNombre() != null ? c.getReceptorNombre() : v.getCliente().getNombreORazonSocial()), bold));
            d.add(new Paragraph("Documento: " + (c.getReceptorNumDoc() != null ? c.getReceptorNumDoc() : "No registrado"), normal));
            d.add(new Paragraph("Comprobante de referencia: " + n.getNumeroComprobanteReferencia(), bold));
            d.add(new Paragraph("Motivo (01 - Anulación de la operación): " + n.getMotivo(), normal));
            PdfPTable tabla = new PdfPTable(new float[]{5,1,2}); tabla.setWidthPercentage(100); tabla.setSpacingBefore(18);
            for (String h : new String[]{"Descripción", "Cant.", "Base sin IGV"}) { PdfPCell cell = new PdfPCell(new Phrase(h,bold)); cell.setPadding(8); cell.setBackgroundColor(new Color(230,240,245)); tabla.addCell(cell); }
            for (var item : v.getDetalles()) {
                tabla.addCell(new Phrase(item.getDescripcion(),normal)); tabla.addCell(new Phrase(String.valueOf(item.getCantidad()),normal));
                tabla.addCell(new Phrase(item.calcularSubtotalDetalle().setScale(2,RoundingMode.HALF_UP).toPlainString(),normal));
            }
            d.add(tabla);
            String moneda = v.getMoneda().name().equals("DOLARES") ? "US$ " : "S/ ";
            Paragraph total = new Paragraph("IMPORTE TOTAL DE LA NOTA: " + moneda + n.getTotal().setScale(2,RoundingMode.HALF_UP),titulo);
            total.setSpacingBefore(20); d.add(total);
            d.add(new Paragraph("Base: " + moneda + v.calcularSubtotal() + " | IGV: " + moneda + v.calcularIgv(), normal));
            Paragraph pie = new Paragraph("Anulación total del comprobante de referencia. La devolución de dinero, si corresponde, se registra por separado en Cobranza. Conserve este PDF junto con el XML y la constancia CDR.",normal);
            pie.setSpacingBefore(22); d.add(pie);
        } catch (Exception e) { throw new IllegalStateException("No se pudo generar el PDF de la nota de crédito",e); }
        return out.toByteArray();
    }
}
