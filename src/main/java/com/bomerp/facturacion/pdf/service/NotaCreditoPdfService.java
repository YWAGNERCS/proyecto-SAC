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
        Font normal = PdfEditorial.texto(9.5f);
        Font bold = PdfEditorial.negrita(10);
        Font titulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, PdfEditorial.TINTA);
        try (Document d = new Document(PageSize.A4, 42, 42, 32, 52)) {
            PdfEditorial.preparar(PdfWriter.getInstance(d, out)); d.open();
            PdfEditorial.encabezado(d,empresa,"NOTA DE CRÉDITO ELECTRÓNICA",n.getSerie()+"-"+n.getNumero());
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
            PdfEditorial.cabecera(tabla,"Descripción",Element.ALIGN_LEFT);
            PdfEditorial.cabecera(tabla,"Cant.",Element.ALIGN_CENTER);
            PdfEditorial.cabecera(tabla,"Base sin IGV",Element.ALIGN_RIGHT);
            tabla.setHeaderRows(1);
            int fila=0;
            for (var item : v.getDetalles()) {
                PdfEditorial.item(tabla,item.getDescripcion(),Element.ALIGN_LEFT,fila);
                PdfEditorial.item(tabla,String.valueOf(item.getCantidad()),Element.ALIGN_CENTER,fila);
                PdfEditorial.item(tabla,item.calcularSubtotalDetalle().setScale(2,RoundingMode.HALF_UP).toPlainString(),Element.ALIGN_RIGHT,fila++);
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
