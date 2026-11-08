package com.bomerp.facturacion.correo.service;

import com.bomerp.config.empresa.EmpresaProperties;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Correo HTML del comprobante electrónico: tablas y estilos en línea (lo que respetan Gmail y Outlook), ancho máximo
 * de 600 px que se adapta al celular, sin JavaScript ni hojas de estilo externas. El logo va incrustado (cid:logo) y
 * no hay ninguna imagen remota indispensable. Todos los datos variables se escapan (no se puede inyectar HTML).
 * Los datos de contacto salen de la configuración (app.empresa.*): lo que no está configurado no se muestra.
 */
public final class PlantillaCorreoComprobante {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String AZUL = "#102A43";
    private static final String CELESTE = "#0078B7";

    private PlantillaCorreoComprobante() {}

    /** Lo que muestra el correo de un comprobante. */
    public record Datos(String tipo, String serieNumero, LocalDate fechaEmision, String moneda, BigDecimal total,
                        boolean observado, String cliente, List<String> adjuntos) {}

    /** "Factura electrónica F001-00000125 — JD REFRIGERACIÓN S.A.C." */
    public static String asunto(Datos d, EmpresaProperties empresa) {
        return titulo(d.tipo()) + " " + d.serieNumero() + " — " + empresa.razonSocial();
    }

    static String titulo(String tipo) {
        return "FACTURA".equals(tipo) ? "Factura electrónica" : "Boleta de venta electrónica";
    }

    static String simbolo(String moneda) {
        return "DOLARES".equals(moneda) ? "US$" : "S/";
    }

    private static String e(String texto) {
        // UTF-8: escapa < > & " ' (lo peligroso) y deja las tildes tal cual
        return texto == null ? "" : HtmlUtils.htmlEscape(texto, "UTF-8");
    }

    private static boolean hay(String texto) {
        return texto != null && !texto.isBlank();
    }

    public static String html(Datos d, EmpresaProperties empresa) {
        String estado = d.observado() ? "Aceptado por SUNAT con observaciones" : "Aceptado por SUNAT";
        String importe = simbolo(d.moneda()) + " " + String.format(java.util.Locale.US, "%,.2f", d.total());

        StringBuilder adjuntos = new StringBuilder();
        for (String nombre : d.adjuntos()) {
            boolean pdf = nombre.toLowerCase().endsWith(".pdf");
            adjuntos.append("<tr><td style=\"padding:6px 0;font-size:14px;color:#334E68;\">")
                    .append("<span style=\"display:inline-block;min-width:38px;padding:2px 6px;margin-right:8px;border-radius:4px;")
                    .append("font-size:11px;font-weight:bold;text-align:center;color:#ffffff;background:")
                    .append(pdf ? "#C0392B" : CELESTE).append(";\">").append(pdf ? "PDF" : "XML").append("</span>")
                    .append(e(nombre)).append("<br><span style=\"font-size:12px;color:#829AB1;\">")
                    .append(pdf ? "Representación impresa del comprobante" : "Comprobante electrónico firmado (archivo válido ante SUNAT)")
                    .append("</span></td></tr>");
        }

        StringBuilder contacto = new StringBuilder();
        if (hay(empresa.telefono())) contacto.append("Teléfono: ").append(e(empresa.telefono())).append("<br>");
        if (hay(empresa.correoContacto())) {
            contacto.append("Correo: <a href=\"mailto:").append(e(empresa.correoContacto()))
                    .append("\" style=\"color:").append(CELESTE).append(";\">").append(e(empresa.correoContacto())).append("</a><br>");
        }
        if (hay(empresa.web())) {
            String web = empresa.web().startsWith("http") ? empresa.web() : "https://" + empresa.web();
            contacto.append("Web: <a href=\"").append(e(web)).append("\" style=\"color:").append(CELESTE).append(";\">")
                    .append(e(empresa.web())).append("</a><br>");
        }

        return """
                <!DOCTYPE html>
                <html lang="es"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
                <meta name="x-apple-disable-message-reformatting"><title>%1$s</title></head>
                <body style="margin:0;padding:0;background:#EEF4F8;">
                <div style="display:none;max-height:0;overflow:hidden;">%2$s %3$s por %4$s. Adjuntamos el PDF y el XML.</div>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="background:#EEF4F8;">
                <tr><td align="center" style="padding:24px 12px;">
                <table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0"
                       style="width:100%%;max-width:600px;background:#ffffff;border-radius:10px;font-family:Arial,Helvetica,sans-serif;">
                  <tr><td style="padding:24px 28px 16px 28px;border-bottom:3px solid %5$s;">
                    <img src="cid:logo" alt="%6$s" width="150" style="display:block;width:150px;max-width:150px;height:auto;border:0;">
                  </td></tr>
                  <tr><td style="padding:28px 28px 8px 28px;">
                    <h1 style="margin:0 0 12px 0;font-size:22px;line-height:28px;color:%7$s;font-weight:bold;">Has recibido tu comprobante electrónico</h1>
                    <p style="margin:0;font-size:15px;line-height:22px;color:#334E68;">Hola, <strong>%8$s</strong>:</p>
                    <p style="margin:8px 0 0 0;font-size:15px;line-height:22px;color:#334E68;">%6$s te envía tu %9$s. Encontrarás el detalle a continuación y los archivos adjuntos a este correo.</p>
                  </td></tr>
                  <tr><td style="padding:16px 28px;">
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="background:#F6FAFC;border:1px solid #D9E8F0;border-radius:8px;">
                      <tr><td style="padding:16px 18px 4px 18px;font-size:12px;color:#829AB1;text-transform:uppercase;letter-spacing:1px;">%2$s</td></tr>
                      <tr><td style="padding:0 18px 12px 18px;font-size:22px;font-weight:bold;color:%7$s;">%3$s</td></tr>
                      <tr><td style="padding:0 18px 16px 18px;">
                        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="font-size:14px;color:#334E68;">
                          <tr><td style="padding:5px 0;color:#627D98;">Fecha de emisión</td><td align="right" style="padding:5px 0;font-weight:bold;">%10$s</td></tr>
                          <tr><td style="padding:5px 0;color:#627D98;">Importe total</td><td align="right" style="padding:5px 0;font-weight:bold;font-size:16px;color:%7$s;">%4$s</td></tr>
                          <tr><td style="padding:5px 0;color:#627D98;">Estado tributario</td><td align="right" style="padding:5px 0;">
                            <span style="display:inline-block;padding:3px 10px;border-radius:12px;background:#E3F8EE;color:#05603A;font-size:12px;font-weight:bold;">%11$s</span></td></tr>
                        </table>
                      </td></tr>
                    </table>
                  </td></tr>
                  <tr><td style="padding:8px 28px 4px 28px;">
                    <p style="margin:0 0 6px 0;font-size:14px;font-weight:bold;color:%7$s;">Archivos adjuntos</p>
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0">%12$s</table>
                    <p style="margin:10px 0 0 0;font-size:12px;line-height:18px;color:#829AB1;">Conserva el XML: es el comprobante electrónico con validez tributaria. El PDF es su representación impresa.</p>
                  </td></tr>
                  <tr><td style="padding:20px 28px 24px 28px;">
                    <p style="margin:0;font-size:15px;line-height:22px;color:#334E68;">Gracias por tu preferencia.</p>
                  </td></tr>
                  <tr><td style="padding:18px 28px;background:%7$s;border-radius:0 0 10px 10px;font-size:12px;line-height:18px;color:#D9E8F0;">
                    <strong style="color:#ffffff;">%6$s</strong><br>
                    RUC %13$s<br>%14$s<br>%15$s
                    <span style="color:#9FB3C8;">Este correo se generó automáticamente al emitirse tu comprobante.</span>
                  </td></tr>
                </table>
                </td></tr></table>
                </body></html>
                """.formatted(
                e(asunto(d, empresa)),                  // 1 título de la página
                e(titulo(d.tipo())),                    // 2 tipo
                e(d.serieNumero()),                     // 3 serie y número
                e(importe),                             // 4 importe
                CELESTE,                                // 5
                e(empresa.razonSocial()),               // 6 empresa
                AZUL,                                   // 7
                e(d.cliente()),                         // 8 cliente
                e(titulo(d.tipo()).toLowerCase()),      // 9
                d.fechaEmision() == null ? "" : d.fechaEmision().format(FECHA), // 10
                e(estado),                              // 11
                adjuntos,                               // 12 (ya escapado)
                e(empresa.ruc()),                       // 13
                e(empresa.direccion()),                 // 14
                contacto.isEmpty() ? "" : contacto.toString());   // 15 (ya escapado)
    }
}
