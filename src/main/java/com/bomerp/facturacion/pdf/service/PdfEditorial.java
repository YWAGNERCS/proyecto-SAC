package com.bomerp.facturacion.pdf.service;

import com.bomerp.config.empresa.EmpresaProperties;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import java.awt.Color;

/** Identidad editorial común a los comprobantes impresos de JD. */
final class PdfEditorial {
    static final Color TINTA = new Color(33,45,54);
    static final Color LINEA = new Color(205,213,220);
    static final Color SUAVE = new Color(245,246,247);
    private PdfEditorial() {}
    static Font texto(float size) { return FontFactory.getFont(FontFactory.HELVETICA,size,TINTA); }
    static Font negrita(float size) { return FontFactory.getFont(FontFactory.HELVETICA_BOLD,size,TINTA); }
    static void preparar(PdfWriter writer) {
        writer.setPageEvent(new PdfPageEventHelper() {
            @Override public void onEndPage(PdfWriter w,Document d) {
                var cb=w.getDirectContent();
                cb.setColorStroke(LINEA);cb.setLineWidth(.5f);cb.moveTo(d.left(),35);cb.lineTo(d.right(),35);cb.stroke();
                ColumnText.showTextAligned(cb,Element.ALIGN_LEFT,new Phrase("JD REFRIGERACIÓN S.A.C.",texto(8)),d.left(),23,0);
                ColumnText.showTextAligned(cb,Element.ALIGN_RIGHT,new Phrase("Página "+w.getPageNumber(),texto(8)),d.right(),23,0);
            }
        });
    }
    static void encabezado(Document d,EmpresaProperties empresa,String tipo,String numero) throws Exception {
        PdfPTable marca=new PdfPTable(1);marca.setWidthPercentage(100);
        PdfPCell top=new PdfPCell();top.setBorder(Rectangle.TOP);top.setBorderWidthTop(3);top.setBorderColor(TINTA);
        top.setPaddingTop(7);top.setPaddingBottom(5);top.setPaddingLeft(0);
        try(var in=PdfEditorial.class.getResourceAsStream("/static/images/logo.png")) {
            if(in!=null){Image logo=Image.getInstance(in.readAllBytes());logo.scaleToFit(190,63);top.addElement(logo);}
            else top.addElement(new Paragraph(empresa.razonSocial(),negrita(12)));
        }
        top.addElement(new Paragraph(empresa.razonSocial()+"\n"+empresa.direccion(),texto(8.5f)));
        marca.addCell(top);d.add(marca);
        Paragraph ruc=new Paragraph("RUC: "+empresa.ruc(),texto(9));ruc.setSpacingBefore(5);d.add(ruc);
        PdfPTable titulo=new PdfPTable(new float[]{3,2});titulo.setWidthPercentage(100);titulo.setSpacingBefore(6);titulo.setSpacingAfter(9);
        PdfPCell izq=new PdfPCell(new Phrase(tipo,FontFactory.getFont(FontFactory.TIMES_ROMAN,23,TINTA)));
        PdfPCell der=new PdfPCell(new Phrase(numero,negrita(12)));
        for(PdfPCell celda:new PdfPCell[]{izq,der}){celda.setBorder(Rectangle.BOTTOM);celda.setBorderColor(TINTA);celda.setPaddingLeft(0);celda.setPaddingBottom(8);celda.setVerticalAlignment(Element.ALIGN_BOTTOM);}
        der.setHorizontalAlignment(Element.ALIGN_RIGHT);titulo.addCell(izq);titulo.addCell(der);d.add(titulo);
    }
    static void cabecera(PdfPTable tabla,String texto,int alineacion) {
        PdfPCell celda=new PdfPCell(new Phrase(texto,negrita(9)));
        celda.setBorder(Rectangle.TOP|Rectangle.BOTTOM);celda.setBorderColor(TINTA);celda.setPadding(8);
        celda.setHorizontalAlignment(alineacion);tabla.addCell(celda);
    }
    static void item(PdfPTable tabla,String texto,int alineacion,int fila) {
        PdfPCell celda=new PdfPCell(new Phrase(texto,texto(9.5f)));
        celda.setBorder(Rectangle.BOTTOM);celda.setBorderColor(LINEA);celda.setPadding(8);
        celda.setVerticalAlignment(Element.ALIGN_TOP);celda.setHorizontalAlignment(alineacion);
        if(fila%2==1)celda.setBackgroundColor(SUAVE);tabla.addCell(celda);
    }
}
