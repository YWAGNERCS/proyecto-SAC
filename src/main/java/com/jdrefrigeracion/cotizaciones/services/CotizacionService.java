package com.jdrefrigeracion.cotizaciones.services;
import com.jdrefrigeracion.ventas.services.VentaService;
import com.jdrefrigeracion.ventas.models.Venta;
import com.jdrefrigeracion.cotizaciones.repositories.CotizacionRepository;
import com.jdrefrigeracion.cotizaciones.models.Cotizacion;
import com.jdrefrigeracion.cotizaciones.models.CotizacionRequest;
import com.jdrefrigeracion.cotizaciones.models.ItemCotizacion;

import com.jdrefrigeracion.catalogo.models.Producto;
import com.jdrefrigeracion.catalogo.repositories.ProductoRepository;
import com.jdrefrigeracion.clientes.integrations.Cliente;
import com.jdrefrigeracion.clientes.repositories.ClienteRepository;
import com.jdrefrigeracion.notificaciones.services.NotificadorCorreo;
import com.jdrefrigeracion.notificaciones.services.NotificadorWhatsApp;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class CotizacionService {

    private final CotizacionRepository cotizacionRepository;
    private final ClienteRepository clienteRepository;
    private final ProductoRepository productoRepository;
    private final NotificadorWhatsApp notificadorWhatsApp;
    private final NotificadorCorreo notificadorCorreo;
    private final GeneradorPdfService generadorPdfService;

    public CotizacionService(CotizacionRepository cotizacionRepository,
                              ClienteRepository clienteRepository,
                              ProductoRepository productoRepository,
                              NotificadorWhatsApp notificadorWhatsApp,
                              NotificadorCorreo notificadorCorreo,
                              GeneradorPdfService generadorPdfService) {
        this.cotizacionRepository = cotizacionRepository;
        this.clienteRepository = clienteRepository;
        this.productoRepository = productoRepository;
        this.notificadorWhatsApp = notificadorWhatsApp;
        this.notificadorCorreo = notificadorCorreo;
        this.generadorPdfService = generadorPdfService;
    }

    public List<Cotizacion> listarTodas() {
        return cotizacionRepository.findAll();
    }

    public List<Cotizacion> listarPorEstado(Cotizacion.EstadoCotizacion estado) {
        return cotizacionRepository.findByEstado(estado);
    }

    /**
     * Crea una cotización a partir del DTO recibido del formulario.
     * El precio de cada item se "congela" con el precio vigente del
     * producto en este momento (ver ItemCotizacion).
     */
    public Cotizacion crear(CotizacionRequest.CrearCotizacionDTO request) {
        Cliente cliente = clienteRepository.findById(request.clienteId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Cliente no encontrado: " + request.clienteId()));

        Cotizacion cotizacion = new Cotizacion();
        cotizacion.setCliente(cliente);
        cotizacion.setMoneda(request.moneda());
        cotizacion.setFechaEmision(LocalDate.now());

        int dias = request.diasVigencia() != null ? request.diasVigencia() : 15; // 15 días por defecto
        cotizacion.setFechaVencimiento(LocalDate.now().plusDays(dias));

        for (CotizacionRequest.ItemDTO itemDTO : request.items()) {
            Producto producto = productoRepository.findById(itemDTO.productoId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Producto no encontrado: " + itemDTO.productoId()));
            cotizacion.agregarItem(new ItemCotizacion(producto, itemDTO.cantidad()));
        }

        return cotizacionRepository.save(cotizacion);
    }

    /** RF confirmado: Lisandro envía cotizaciones solo por WhatsApp y correo */
    public void enviar(Long cotizacionId, boolean porWhatsApp, boolean porCorreo) {
        Cotizacion cotizacion = obtenerOFallar(cotizacionId);
        String resumen = "Cotización #" + cotizacion.getId() + " por un total de "
                + cotizacion.calcularTotal() + " " + cotizacion.getMoneda();

        if (porWhatsApp && cotizacion.getCliente().getTelefono() != null) {
            notificadorWhatsApp.enviarWhatsApp(cotizacion.getCliente().getTelefono(), resumen);
            cotizacion.setEnviadoWhatsApp(true);
        }
        if (porCorreo && cotizacion.getCliente().getCorreo() != null) {
            String cuerpoHtml = generarHtmlCotizacion(cotizacion);
            java.io.File pdfAdjunto = null;
            try {
                pdfAdjunto = generadorPdfService.generarCotizacionPdf(cotizacion);
                notificadorCorreo.enviarCorreoConAdjunto(cotizacion.getCliente().getCorreo(),
                        "Cotización JD Refrigeración S.A.C.", cuerpoHtml, pdfAdjunto);
            } catch (Exception e) {
                System.err.println("Error generando PDF: " + e.getMessage());
                // Fallback sin PDF
                notificadorCorreo.enviarCorreo(cotizacion.getCliente().getCorreo(),
                        "Cotización JD Refrigeración S.A.C.", cuerpoHtml);
            } finally {
                if (pdfAdjunto != null && pdfAdjunto.exists()) {
                    pdfAdjunto.delete(); // Limpiar archivo temporal
                }
            }
            cotizacion.setEnviadoCorreo(true);
        }
        cotizacionRepository.save(cotizacion);
    }

    private String generarHtmlCotizacion(Cotizacion cotizacion) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><body style='font-family: Arial, sans-serif; background-color: #1b1b1b; color: #ffffff; padding: 20px; margin: 0;'>");
        sb.append("<div style='max-width: 600px; margin: 0 auto; background-color: #242424; border-radius: 8px; padding: 30px; box-shadow: 0 4px 8px rgba(0,0,0,0.2);'>");
        sb.append("<div style='text-align: left; margin-bottom: 20px;'><img src='cid:logo' alt='JD Refrigeración' style='max-height: 80px;' /></div>");
        sb.append("<h2 style='color: #ffffff; margin-bottom: 10px;'>Gracias por cotizar con nosotros el ").append(cotizacion.getFechaEmision()).append("</h2>");
        sb.append("<p style='color: #cccccc; margin-bottom: 30px;'>A continuación, se detallan los productos y servicios solicitados para su proyecto.</p>");
        
        sb.append("<p style='color: #aaaaaa; font-size: 14px; margin: 5px 0;'>Número de cotización: ").append(cotizacion.getId()).append("</p>");
        sb.append("<p style='color: #aaaaaa; font-size: 14px; margin: 5px 0;'>Válido hasta: ").append(cotizacion.getFechaVencimiento()).append("</p>");
        
        sb.append("<h3 style='color: #ffffff; margin-top: 30px; border-bottom: 1px solid #444; padding-bottom: 10px;'>Detalles de la cotización</h3>");
        
        sb.append("<table style='width: 100%; border-collapse: collapse; margin-top: 15px;'>");
        sb.append("<tr style='border-bottom: 1px solid #444;'>");
        sb.append("<th style='text-align: left; padding: 10px 0; color: #aaaaaa; font-size: 12px;'>Descripción del elemento</th>");
        sb.append("<th style='text-align: center; padding: 10px 0; color: #aaaaaa; font-size: 12px;'>Cantidad</th>");
        sb.append("<th style='text-align: right; padding: 10px 0; color: #aaaaaa; font-size: 12px;'>Precio</th>");
        sb.append("</tr>");
        
        for (ItemCotizacion item : cotizacion.getItems()) {
            sb.append("<tr style='border-bottom: 1px solid #333;'>");
            sb.append("<td style='padding: 15px 0;'>")
              .append("<strong style='display: block; color: #ffffff;'>").append(item.getProducto().getNombre()).append("</strong>")
              .append("<span style='font-size: 12px; color: #888888;'>").append(item.getProducto().getDescripcion()).append("</span>")
              .append("</td>");
            sb.append("<td style='padding: 15px 0; text-align: center; color: #ffffff;'>").append(item.getCantidad()).append("</td>");
            sb.append("<td style='padding: 15px 0; text-align: right; color: #ffffff;'>").append(cotizacion.getMoneda()).append(" ").append(item.calcularSubtotalItem()).append("</td>");
            sb.append("</tr>");
        }
        sb.append("</table>");
        
        sb.append("<table style='width: 100%; margin-top: 30px; border-top: 1px solid #444; padding-top: 15px;'>");
        sb.append("<tr><td style='color: #aaaaaa; padding: 5px 0;'>Subtotal de la cotización</td><td style='text-align: right; color: #ffffff;'>").append(cotizacion.getMoneda()).append(" ").append(cotizacion.calcularSubtotal()).append("</td></tr>");
        sb.append("<tr><td style='color: #aaaaaa; padding: 5px 0;'>IGV (18%)</td><td style='text-align: right; color: #ffffff;'>").append(cotizacion.getMoneda()).append(" ").append(cotizacion.calcularIgv()).append("</td></tr>");
        sb.append("<tr><td style='color: #ffffff; font-weight: bold; font-size: 18px; padding: 10px 0;'>Total de la cotización</td><td style='text-align: right; color: #ffffff; font-weight: bold; font-size: 18px;'>").append(cotizacion.getMoneda()).append(" ").append(cotizacion.calcularTotal()).append("</td></tr>");
        sb.append("</table>");
        
        sb.append("<div style='margin-top: 40px; background-color: #333; padding: 20px; border-radius: 8px;'>");
        sb.append("<h4 style='color: #ffffff; margin-top: 0;'>Aprobación</h4>");
        sb.append("<p style='color: #aaaaaa; font-size: 14px;'>Para aceptar esta cotización, por favor responda a este correo o comuníquese con su asesor de ventas al número de atención al cliente.</p>");
        sb.append("<a href='#' style='display: inline-block; background-color: #0078d4; color: white; text-decoration: none; padding: 10px 20px; border-radius: 4px; font-weight: bold; margin-top: 10px;'>Ver o administrar la cotización &gt;</a>");
        sb.append("</div>");
        
        sb.append("</div></body></html>");
        
        return sb.toString();
    }

    /**
     * Marca la cotización como aceptada. La conversión real a Venta
     * (RF: "convertir cotización aceptada en venta sin doble digitación")
     * se implementa en VentaService, que reutiliza este mismo objeto
     * Cotizacion como fuente de datos — se conecta cuando se construya
     * el módulo de Ventas.
     */
    public Cotizacion aceptar(Long cotizacionId) {
        Cotizacion cotizacion = obtenerOFallar(cotizacionId);
        validarNoVencida(cotizacion);
        cotizacion.setEstado(Cotizacion.EstadoCotizacion.ACEPTADA);
        return cotizacionRepository.save(cotizacion);
    }

    public Cotizacion rechazar(Long cotizacionId) {
        Cotizacion cotizacion = obtenerOFallar(cotizacionId);
        cotizacion.setEstado(Cotizacion.EstadoCotizacion.RECHAZADA);
        return cotizacionRepository.save(cotizacion);
    }

    /**
     * Job de mantenimiento: marca como VENCIDA cualquier cotización
     * pendiente cuya fecha de vencimiento ya pasó. Se puede invocar
     * desde un @Scheduled diario en el proyecto final.
     */
    public void marcarVencidas() {
        List<Cotizacion> vencidas = cotizacionRepository
                .findByEstadoAndFechaVencimientoBefore(
                        Cotizacion.EstadoCotizacion.PENDIENTE, LocalDate.now());
        for (Cotizacion c : vencidas) {
            c.setEstado(Cotizacion.EstadoCotizacion.VENCIDA);
        }
        cotizacionRepository.saveAll(vencidas);
    }

    private void validarNoVencida(Cotizacion cotizacion) {
        if (cotizacion.getFechaVencimiento() != null
                && cotizacion.getFechaVencimiento().isBefore(LocalDate.now())) {
            throw new IllegalStateException(
                    "La cotización #" + cotizacion.getId() + " ya venció y no puede aceptarse");
        }
    }

    private Cotizacion obtenerOFallar(Long id) {
        return cotizacionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cotización no encontrada: " + id));
    }
}
