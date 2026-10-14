package com.bomerp.proformas.proforma.service;
import com.bomerp.proformas.proformarequest.entity.ProformaRequest;
import com.bomerp.proformas.pdf.service.GeneradorPdfService;
import com.bomerp.ventas.venta.entity.Venta;
import com.bomerp.proformas.proforma.repository.ProformaRepository;
import com.bomerp.proformas.proforma.entity.Proforma;
import com.bomerp.proformas.itemproforma.entity.ItemProforma;

import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.catalogo.producto.service.ProductoService;
import com.bomerp.clientes.general.integration.Cliente;
import com.bomerp.clientes.cliente.service.ClienteService;
import com.bomerp.config.RecursoNoEncontradoException;
import com.bomerp.notificaciones.notificacion.service.NotificadorCorreo;
import com.bomerp.notificaciones.notificacion.service.NotificadorWhatsApp;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class ProformaService {

    private final ProformaRepository proformaRepository;
    private final ClienteService clienteService;
    private final ProductoService productoService;
    private final NotificadorWhatsApp notificadorWhatsApp;
    private final NotificadorCorreo notificadorCorreo;
    private final GeneradorPdfService generadorPdfService;

    public ProformaService(ProformaRepository proformaRepository,
                              ClienteService clienteService,
                              ProductoService productoService,
                              NotificadorWhatsApp notificadorWhatsApp,
                              NotificadorCorreo notificadorCorreo,
                              GeneradorPdfService generadorPdfService) {
        this.proformaRepository = proformaRepository;
        this.clienteService = clienteService;
        this.productoService = productoService;
        this.notificadorWhatsApp = notificadorWhatsApp;
        this.notificadorCorreo = notificadorCorreo;
        this.generadorPdfService = generadorPdfService;
    }

    public List<Proforma> listarTodas() {
        return proformaRepository.findAll();
    }

    public List<Proforma> listarPorEstado(Proforma.EstadoProforma estado) {
        return proformaRepository.findByEstado(estado);
    }

    /**
     * Crea una proforma a partir del DTO recibido del formulario.
     * El precio de cada item se "congela" con el precio vigente del
     * producto en este momento (ver ItemProforma).
     */
    public Proforma crear(ProformaRequest.CrearProformaDTO request) {
        Cliente cliente = clienteService.obtenerPorId(request.clienteId());

        Proforma proforma = new Proforma();
        proforma.setCliente(cliente);
        proforma.setMoneda(request.moneda());
        proforma.setFechaEmision(LocalDate.now());

        int dias = request.diasVigencia() != null ? request.diasVigencia() : 15; // 15 días por defecto
        proforma.setFechaVencimiento(LocalDate.now().plusDays(dias));

        for (ProformaRequest.ItemDTO itemDTO : request.items()) {
            Producto producto = productoService.obtenerPorId(itemDTO.productoId());
            proforma.agregarItem(new ItemProforma(producto, itemDTO.cantidad()));
        }

        return proformaRepository.save(proforma);
    }

    /** RF confirmado: Lisandro envía proformas solo por WhatsApp y correo */
    public void enviar(Long proformaId, boolean porWhatsApp, boolean porCorreo) {
        Proforma proforma = obtenerOFallar(proformaId);
        String resumen = "Proforma #" + proforma.getId() + " por un total de "
                + proforma.calcularTotal() + " " + proforma.getMoneda();

        if (porWhatsApp && proforma.getCliente().getTelefono() != null) {
            notificadorWhatsApp.enviarWhatsApp(proforma.getCliente().getTelefono(), resumen);
            proforma.setEnviadoWhatsApp(true);
        }
        if (porCorreo && proforma.getCliente().getCorreo() != null) {
            String cuerpoHtml = generarHtmlProforma(proforma);
            java.io.File pdfAdjunto = null;
            try {
                pdfAdjunto = generadorPdfService.generarProformaPdf(proforma);
                notificadorCorreo.enviarCorreoConAdjunto(proforma.getCliente().getCorreo(),
                        "Proforma JD Refrigeración S.A.C.", cuerpoHtml, pdfAdjunto);
            } catch (Exception e) {
                System.err.println("Error generando PDF: " + e.getMessage());
                // Fallback sin PDF
                notificadorCorreo.enviarCorreo(proforma.getCliente().getCorreo(),
                        "Proforma JD Refrigeración S.A.C.", cuerpoHtml);
            } finally {
                if (pdfAdjunto != null && pdfAdjunto.exists()) {
                    pdfAdjunto.delete(); // Limpiar archivo temporal
                }
            }
            proforma.setEnviadoCorreo(true);
        }
        proformaRepository.save(proforma);
    }

    private String generarHtmlProforma(Proforma proforma) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><body style='font-family: Arial, sans-serif; background-color: #1b1b1b; color: #ffffff; padding: 20px; margin: 0;'>");
        sb.append("<div style='max-width: 600px; margin: 0 auto; background-color: #242424; border-radius: 8px; padding: 30px; box-shadow: 0 4px 8px rgba(0,0,0,0.2);'>");
        sb.append("<div style='text-align: left; margin-bottom: 20px;'><img src='cid:logo' alt='JD Refrigeración' style='max-height: 80px;' /></div>");
        sb.append("<h2 style='color: #ffffff; margin-bottom: 10px;'>Gracias por cotizar con nosotros el ").append(proforma.getFechaEmision()).append("</h2>");
        sb.append("<p style='color: #cccccc; margin-bottom: 30px;'>A continuación, se detallan los productos y servicios solicitados para su proyecto.</p>");
        
        sb.append("<p style='color: #aaaaaa; font-size: 14px; margin: 5px 0;'>Número de proforma: ").append(proforma.getId()).append("</p>");
        sb.append("<p style='color: #aaaaaa; font-size: 14px; margin: 5px 0;'>Válido hasta: ").append(proforma.getFechaVencimiento()).append("</p>");
        
        sb.append("<h3 style='color: #ffffff; margin-top: 30px; border-bottom: 1px solid #444; padding-bottom: 10px;'>Detalles de la proforma</h3>");
        
        sb.append("<table style='width: 100%; border-collapse: collapse; margin-top: 15px;'>");
        sb.append("<tr style='border-bottom: 1px solid #444;'>");
        sb.append("<th style='text-align: left; padding: 10px 0; color: #aaaaaa; font-size: 12px;'>Descripción del elemento</th>");
        sb.append("<th style='text-align: center; padding: 10px 0; color: #aaaaaa; font-size: 12px;'>Cantidad</th>");
        sb.append("<th style='text-align: right; padding: 10px 0; color: #aaaaaa; font-size: 12px;'>Precio</th>");
        sb.append("</tr>");
        
        for (ItemProforma item : proforma.getItems()) {
            sb.append("<tr style='border-bottom: 1px solid #333;'>");
            sb.append("<td style='padding: 15px 0;'>")
              .append("<strong style='display: block; color: #ffffff;'>").append(item.getProducto().getNombre()).append("</strong>")
              .append("<span style='font-size: 12px; color: #888888;'>").append(item.getProducto().getDescripcion()).append("</span>")
              .append("</td>");
            sb.append("<td style='padding: 15px 0; text-align: center; color: #ffffff;'>").append(item.getCantidad()).append("</td>");
            sb.append("<td style='padding: 15px 0; text-align: right; color: #ffffff;'>").append(proforma.getMoneda()).append(" ").append(item.calcularSubtotalItem()).append("</td>");
            sb.append("</tr>");
        }
        sb.append("</table>");
        
        sb.append("<table style='width: 100%; margin-top: 30px; border-top: 1px solid #444; padding-top: 15px;'>");
        sb.append("<tr><td style='color: #aaaaaa; padding: 5px 0;'>Subtotal de la proforma</td><td style='text-align: right; color: #ffffff;'>").append(proforma.getMoneda()).append(" ").append(proforma.calcularSubtotal()).append("</td></tr>");
        sb.append("<tr><td style='color: #aaaaaa; padding: 5px 0;'>IGV (18%)</td><td style='text-align: right; color: #ffffff;'>").append(proforma.getMoneda()).append(" ").append(proforma.calcularIgv()).append("</td></tr>");
        sb.append("<tr><td style='color: #ffffff; font-weight: bold; font-size: 18px; padding: 10px 0;'>Total de la proforma</td><td style='text-align: right; color: #ffffff; font-weight: bold; font-size: 18px;'>").append(proforma.getMoneda()).append(" ").append(proforma.calcularTotal()).append("</td></tr>");
        sb.append("</table>");
        
        sb.append("<div style='margin-top: 40px; background-color: #333; padding: 20px; border-radius: 8px;'>");
        sb.append("<h4 style='color: #ffffff; margin-top: 0;'>Aprobación</h4>");
        sb.append("<p style='color: #aaaaaa; font-size: 14px;'>Para aceptar esta proforma, por favor responda a este correo o comuníquese con su asesor de ventas al número de atención al cliente.</p>");
        sb.append("<a href='#' style='display: inline-block; background-color: #0078d4; color: white; text-decoration: none; padding: 10px 20px; border-radius: 4px; font-weight: bold; margin-top: 10px;'>Ver o administrar la proforma &gt;</a>");
        sb.append("</div>");
        
        sb.append("</div></body></html>");
        
        return sb.toString();
    }

    /**
     * Marca la proforma como aceptada. La conversión real a Venta
     * (RF: "convertir proforma aceptada en venta sin doble digitación")
     * se implementa en VentaService, que reutiliza este mismo objeto
     * Proforma como fuente de datos — se conecta cuando se construya
     * el módulo de Ventas.
     */
    public Proforma aceptar(Long proformaId) {
        Proforma proforma = obtenerOFallar(proformaId);
        validarNoVencida(proforma);
        proforma.setEstado(Proforma.EstadoProforma.ACEPTADA);
        return proformaRepository.save(proforma);
    }

    public Proforma rechazar(Long proformaId) {
        Proforma proforma = obtenerOFallar(proformaId);
        proforma.setEstado(Proforma.EstadoProforma.RECHAZADA);
        return proformaRepository.save(proforma);
    }

    /**
     * Job de mantenimiento: marca como VENCIDA cualquier proforma
     * pendiente cuya fecha de vencimiento ya pasó. Se puede invocar
     * desde un @Scheduled diario en el proyecto final.
     */
    public void marcarVencidas() {
        List<Proforma> vencidas = proformaRepository
                .findByEstadoAndFechaVencimientoBefore(
                        Proforma.EstadoProforma.PENDIENTE, LocalDate.now());
        for (Proforma c : vencidas) {
            c.setEstado(Proforma.EstadoProforma.VENCIDA);
        }
        proformaRepository.saveAll(vencidas);
    }

    private void validarNoVencida(Proforma proforma) {
        if (proforma.getFechaVencimiento() != null
                && proforma.getFechaVencimiento().isBefore(LocalDate.now())) {
            throw new IllegalStateException(
                    "La proforma #" + proforma.getId() + " ya venció y no puede aceptarse");
        }
    }

    /** Fachada pública para otros módulos (ventas, reportes). */
    public Proforma obtenerPorId(Long id) {
        return obtenerOFallar(id);
    }

    private Proforma obtenerOFallar(Long id) {
        return proformaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Proforma no encontrada: " + id));
    }
}
