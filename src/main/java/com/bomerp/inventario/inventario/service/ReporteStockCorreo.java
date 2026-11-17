package com.bomerp.inventario.inventario.service;

import com.bomerp.catalogo.producto.entity.Producto;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** Plantilla de alerta de inventario con estilos en línea para clientes de correo. */
final class ReporteStockCorreo {
    private ReporteStockCorreo() {}

    static String generar(List<Producto> productos) {
        long bajoMinimo = productos.stream().filter(ReporteStockCorreo::bajoMinimo).count();
        String filas = productos.stream()
                .sorted(Comparator.comparing(p -> !bajoMinimo(p)))
                .map(ReporteStockCorreo::fila)
                .collect(Collectors.joining());
        return """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>Alerta de inventario</title>
                </head>
                <body style="margin:0;background:#edf5f8;color:#17324a;font-family:Arial,Helvetica,sans-serif;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#edf5f8;">
                    <tr><td align="center" style="padding:24px 12px;">
                      <table role="presentation" width="600" cellpadding="0" cellspacing="0"
                             style="width:100%%;max-width:600px;background:#ffffff;border:1px solid #d9e7ee;border-radius:12px;">
                        <tr><td style="padding:20px 24px;border-bottom:4px solid #5bc4df;">
                          <img src="cid:logo" width="148" alt="JD Refrigeración" style="display:block;width:148px;height:auto;border:0;">
                        </td></tr>
                        <tr><td style="padding:24px;background:#123851;color:#ffffff;">
                          <h1 style="margin:0 0 8px;font-size:26px;line-height:32px;">Alerta de inventario</h1>
                          <p style="margin:0;color:#d5eef6;font-size:15px;line-height:22px;">Productos en el mínimo o por debajo.</p>
                        </td></tr>
                        <tr><td style="padding:20px 20px 0;">
                          <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
                            <tr>
                              <td width="50%%" style="padding:12px;background:#fff1ec;color:#8b301b;">
                                <strong style="display:block;font-size:23px;line-height:27px;">%d</strong>
                                <span style="font-size:13px;">Por debajo del mínimo</span>
                              </td>
                              <td width="50%%" style="padding:12px;background:#fff7df;color:#704600;border-left:6px solid #ffffff;">
                                <strong style="display:block;font-size:23px;line-height:27px;">%d</strong>
                                <span style="font-size:13px;">En el mínimo</span>
                              </td>
                            </tr>
                          </table>
                        </td></tr>
                        <tr><td style="padding:20px;">
                          <table aria-label="Productos en alerta" width="100%%" cellpadding="0" cellspacing="0"
                                 style="border-collapse:collapse;font-size:13px;line-height:19px;">
                            <thead><tr style="background:#edf5f8;color:#17324a;">
                              <th scope="col" align="left" style="padding:9px 8px;">Producto</th>
                              <th scope="col" align="right" style="padding:9px 8px;">Actual</th>
                              <th scope="col" align="right" style="padding:9px 8px;">Mín.</th>
                              <th scope="col" align="left" style="padding:9px 8px;">Estado</th>
                            </tr></thead>
                            <tbody>%s</tbody>
                          </table>
                        </td></tr>
                        <tr><td style="padding:12px 20px;border-top:1px solid #d9e7ee;color:#526a7d;font-size:12px;">
                          SGE-JD · Reporte interno de inventario
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(bajoMinimo, productos.size() - bajoMinimo, filas);
    }

    private static boolean bajoMinimo(Producto producto) {
        return producto.getStockActual() < producto.getStockMinimo();
    }

    private static String fila(Producto producto) {
        boolean bajo = bajoMinimo(producto);
        String estado = bajo ? "Bajo mínimo" : "En el mínimo";
        String color = bajo ? "#8b301b" : "#704600";
        return """
                <tr>
                  <th scope="row" align="left" style="padding:10px 8px;border-bottom:1px solid #d9e7ee;overflow-wrap:anywhere;">
                    %s<br><span style="font-size:11px;font-weight:normal;color:#526a7d;">#%s</span>
                  </th>
                  <td align="right" style="padding:10px 8px;border-bottom:1px solid #d9e7ee;"><strong>%d</strong></td>
                  <td align="right" style="padding:10px 8px;border-bottom:1px solid #d9e7ee;">%d</td>
                  <td style="padding:10px 8px;border-bottom:1px solid #d9e7ee;color:%s;"><strong>%s</strong></td>
                </tr>
                """.formatted(HtmlUtils.htmlEscape(producto.getNombre(), StandardCharsets.UTF_8.name()), producto.getId(),
                producto.getStockActual(), producto.getStockMinimo(), color, estado);
    }
}
