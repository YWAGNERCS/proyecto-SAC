package com.bomerp.facturacion.sunat.integration;

import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.clientes.general.integration.Cliente;
import com.bomerp.config.empresa.EmpresaProperties;
import com.bomerp.facturacion.comprobante.entity.Comprobante;
import com.bomerp.facturacion.notacredito.entity.NotaCredito;
import com.bomerp.ventas.detalleventa.entity.DetalleVenta;
import com.bomerp.ventas.venta.entity.Venta;
import io.github.project.openubl.xbuilder.content.models.common.Direccion;
import io.github.project.openubl.xbuilder.content.models.common.Firmante;
import io.github.project.openubl.xbuilder.content.models.common.Proveedor;
import io.github.project.openubl.xbuilder.content.models.standard.general.CreditNote;
import io.github.project.openubl.xbuilder.content.models.standard.general.CuotaDePago;
import io.github.project.openubl.xbuilder.content.models.standard.general.Detraccion;
import io.github.project.openubl.xbuilder.content.models.standard.general.DocumentoVentaDetalle;
import io.github.project.openubl.xbuilder.content.models.standard.general.FormaDePago;
import io.github.project.openubl.xbuilder.content.models.standard.general.Invoice;
import io.github.project.openubl.xbuilder.enricher.ContentEnricher;
import io.github.project.openubl.xbuilder.enricher.config.DateProvider;
import io.github.project.openubl.xbuilder.enricher.config.Defaults;
import io.github.project.openubl.xbuilder.renderer.TemplateProducer;
import io.github.project.openubl.xbuilder.signature.XMLSigner;
import io.github.project.openubl.xbuilder.signature.XmlSignatureHelper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Arma y firma el XML UBL 2.1 que exige SUNAT para facturas, boletas y notas de crédito
 * (con xbuilder de OpenUBL: plantillas oficiales, catálogos y totales calculados).
 *
 * Precios: SGE-JD guarda el precio unitario SIN IGV y el descuento por línea; aquí cada línea
 * va con su valor unitario ya descontado y la operación gravada con IGV (código 10).
 */
class XmlComprobante {

    /** Catálogo 54 de SUNAT: mantenimiento y reparación de bienes muebles (servicios de JD). */
    private static final String BIEN_DETRAIDO_MANTENIMIENTO = "020";
    /** Catálogo 59: depósito en cuenta (la cuenta de detracciones del Banco de la Nación). */
    private static final String MEDIO_PAGO_DEPOSITO = "001";

    private final EmpresaProperties empresa;
    private final ContentEnricher enricher;
    private final TemplateProducer plantillas = new TemplateProducer();

    XmlComprobante(EmpresaProperties empresa) {
        this.empresa = empresa;
        Defaults defaults = Defaults.builder()
                .igvTasa(new BigDecimal("0.18")).icbTasa(new BigDecimal("0.5")).ivapTasa(new BigDecimal("0.04"))
                .build();
        DateProvider hoy = LocalDate::now;
        this.enricher = new ContentEnricher(defaults, hoy);
    }

    /** Nombre que SUNAT exige para el archivo: RUC-TIPO-SERIE-NUMERO (sin extensión). */
    String nombreArchivo(String tipoDocumento, String serie, String numero) {
        return empresa.ruc() + "-" + tipoDocumento + "-" + serie + "-" + Integer.parseInt(numero);
    }

    static String tipoDocumento(Comprobante c) {
        return c.getTipo() == Comprobante.TipoComprobante.FACTURA ? "01" : "03";
    }

    /** Factura (01) o boleta (03) de la venta del comprobante, firmada (bytes tal como se firmaron). */
    byte[] factura(Comprobante comprobante, CertificadoSunat certificado) {
        Venta venta = comprobante.getVenta();
        Invoice.InvoiceBuilder<?, ?> b = Invoice.builder()
                .tipoComprobante(tipoDocumento(comprobante))
                .serie(comprobante.getSerie())
                .numero(Integer.parseInt(comprobante.getNumero()))
                .fechaEmision(comprobante.getFechaEmision())
                .moneda(moneda(venta))
                .proveedor(proveedor())
                .firmante(firmante())
                .cliente(cliente(venta.getCliente()))
                .detalles(detalles(venta.getDetalles()));

        if (venta.getTipoPago() == Venta.TipoPago.CREDITO && venta.getFechaLimitePago() != null) {
            b.formaDePago(FormaDePago.builder()
                    .cuota(CuotaDePago.builder().importe(neto(venta)).fechaPago(venta.getFechaLimitePago()).build())
                    .build());
        }
        if (venta.getTipoDeduccion() == Venta.TipoDeduccion.DETRACCION && venta.getMontoDeduccion() != null) {
            b.detraccion(Detraccion.builder()
                    .medioDePago(MEDIO_PAGO_DEPOSITO)
                    .cuentaBancaria(empresa.cuentaDetracciones())
                    .tipoBienDetraido(BIEN_DETRAIDO_MANTENIMIENTO)
                    .porcentaje(venta.getPorcentajeDeduccion().movePointLeft(2))
                    .monto(venta.getMontoDeduccion())
                    .build());
        }

        Invoice invoice = b.build();
        enricher.enrich(invoice);
        return firmar(plantillas.getInvoice().data(invoice).render(), certificado);
    }

    /** Nota de crédito (07) que anula por completo el comprobante de referencia (catálogo 09: 01). */
    byte[] notaCredito(NotaCredito nota, CertificadoSunat certificado) {
        Comprobante original = nota.getComprobanteReferencia();
        Venta venta = original.getVenta();
        CreditNote creditNote = CreditNote.builder()
                .serie(nota.getSerie())
                .numero(Integer.parseInt(nota.getNumero()))
                .fechaEmision(nota.getFechaEmision())
                .moneda(moneda(venta))
                .proveedor(proveedor())
                .firmante(firmante())
                .cliente(cliente(venta.getCliente()))
                .comprobanteAfectadoSerieNumero(original.getSerie() + "-" + Integer.parseInt(original.getNumero()))
                .comprobanteAfectadoTipo(tipoDocumento(original))
                .tipoNota("01")
                .sustentoDescripcion(nota.getMotivo() == null || nota.getMotivo().isBlank() ? "Anulación de la operación" : nota.getMotivo())
                .detalles(detalles(venta.getDetalles()))
                .build();
        enricher.enrich(creditNote);
        return firmar(plantillas.getCreditNote().data(creditNote).render(), certificado);
    }

    /**
     * Devuelve los bytes exactos del XML firmado (xbuilder lo serializa en ISO-8859-1). No se
     * convierten a String: re-codificar las tildes cambiaría el contenido y SUNAT respondería
     * "documento alterado" (el digest de la firma ya no coincide).
     */
    private static byte[] firmar(String xml, CertificadoSunat certificado) {
        try {
            var documento = XMLSigner.signXML(xml, "SGEJD", certificado.certificado(), certificado.clavePrivada());
            return XmlSignatureHelper.getBytesFromDocument(documento);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo firmar el XML del comprobante: " + e.getMessage(), e);
        }
    }

    private Proveedor proveedor() {
        return Proveedor.builder()
                .ruc(empresa.ruc())
                .razonSocial(empresa.razonSocial())
                .direccion(Direccion.builder().direccion(empresa.direccion()).codigoLocal("0000").build())
                .build();
    }

    private Firmante firmante() {
        return Firmante.builder().ruc(empresa.ruc()).razonSocial(empresa.razonSocial()).build();
    }

    /** Catálogo 06: 6 = RUC, 1 = DNI, 0 = sin documento (boletas menores). */
    private static io.github.project.openubl.xbuilder.content.models.common.Cliente cliente(Cliente c) {
        String tipo;
        String numero;
        if (c.getRuc() != null && !c.getRuc().isBlank()) {
            tipo = "6";
            numero = c.getRuc();
        } else if (c.getDni() != null && !c.getDni().isBlank()) {
            tipo = "1";
            numero = c.getDni();
        } else {
            tipo = "0";
            numero = "-";
        }
        return io.github.project.openubl.xbuilder.content.models.common.Cliente.builder()
                .tipoDocumentoIdentidad(tipo)
                .numeroDocumentoIdentidad(numero)
                .nombre(c.getNombreORazonSocial())
                .build();
    }

    /** Cada línea con su valor unitario sin IGV y ya descontado; servicios en ZZ, bienes en NIU. */
    private static List<DocumentoVentaDetalle> detalles(List<DetalleVenta> lineas) {
        return lineas.stream().map(d -> DocumentoVentaDetalle.builder()
                .descripcion(d.getProducto().getNombre())
                .unidadMedida(d.getProducto().getSector() == Producto.Sector.SERVICIO ? "ZZ" : "NIU")
                .cantidad(BigDecimal.valueOf(d.getCantidad()))
                .precio(d.calcularSubtotalDetalle().divide(BigDecimal.valueOf(d.getCantidad()), 10, RoundingMode.HALF_UP))
                .build()).toList();
    }

    private static String moneda(Venta venta) {
        return venta.getMoneda() == Venta.Moneda.DOLARES ? "USD" : "PEN";
    }

    /** Lo que el cliente paga a JD: total menos detracción o retención (la cuota de una venta a crédito). */
    private static BigDecimal neto(Venta venta) {
        BigDecimal deduccion = venta.getMontoDeduccion() == null ? BigDecimal.ZERO : venta.getMontoDeduccion();
        return venta.calcularTotal().subtract(deduccion);
    }
}
