package com.bomerp.proformas.proforma.service;
import com.bomerp.config.ReglaNegocioException;
import com.bomerp.seguridad.descuento.TopeDescuentoService;
import com.bomerp.config.tipocambio.TipoCambioService;
import com.bomerp.proformas.proformarequest.entity.ProformaRequest;
import com.bomerp.proformas.pdf.service.GeneradorPdfService;
import com.bomerp.ventas.venta.entity.Venta;
import com.bomerp.proformas.proforma.dto.ProformaResponseDTO;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
public class ProformaService {

    private final ProformaRepository proformaRepository;
    private final ClienteService clienteService;
    private final ProductoService productoService;
    private final NotificadorWhatsApp notificadorWhatsApp;
    private final NotificadorCorreo notificadorCorreo;
    private final GeneradorPdfService generadorPdfService;
    private final TipoCambioService tipoCambioService;
    private final TopeDescuentoService topeDescuentoService;

    public ProformaService(ProformaRepository proformaRepository,
                              ClienteService clienteService,
                              ProductoService productoService,
                              NotificadorWhatsApp notificadorWhatsApp,
                              NotificadorCorreo notificadorCorreo,
                              GeneradorPdfService generadorPdfService,
                              TipoCambioService tipoCambioService,
                              TopeDescuentoService topeDescuentoService) {
        this.proformaRepository = proformaRepository;
        this.clienteService = clienteService;
        this.productoService = productoService;
        this.notificadorWhatsApp = notificadorWhatsApp;
        this.notificadorCorreo = notificadorCorreo;
        this.generadorPdfService = generadorPdfService;
        this.tipoCambioService = tipoCambioService;
        this.topeDescuentoService = topeDescuentoService;
    }

    /** Lista para el controller: el DTO lee los ítems (LAZY), así que se arma dentro de la transacción. */
    @Transactional(readOnly = true)
    public List<ProformaResponseDTO> listar(Proforma.EstadoProforma estado, Long clienteId) {
        List<Proforma> proformas = estado != null ? proformaRepository.findByEstado(estado) : proformaRepository.findAll();
        return proformas.stream()
                .filter(p -> clienteId == null || p.getCliente().getId().equals(clienteId))
                .sorted(java.util.Comparator.comparing(Proforma::getId).reversed())
                .map(ProformaResponseDTO::desdeEntidad)
                .toList();
    }

    /** Fachada para Reportes: proformas PENDIENTES (entidades, con su cliente e ítems). */
    @Transactional(readOnly = true)
    public List<Proforma> pendientes() {
        return proformaRepository.findByEstado(Proforma.EstadoProforma.PENDIENTE);
    }

    /**
     * Crea una proforma a partir del DTO recibido del formulario.
     * El precio de cada item se "congela" con el precio vigente del
     * producto en este momento (ver ItemProforma).
     */
    public ProformaResponseDTO crear(ProformaRequest.CrearProformaDTO request) {
        Cliente cliente = clienteService.obtenerPorId(request.clienteId());

        Proforma proforma = new Proforma();
        proforma.setCliente(cliente);
        proforma.setMoneda(request.moneda());
        proforma.setFechaEmision(LocalDate.now());

        int dias = request.diasVigencia() != null ? request.diasVigencia() : 15; // 15 días por defecto
        proforma.setFechaVencimiento(LocalDate.now().plusDays(dias));

        // Los productos pueden estar en otra moneda: su precio se convierte a la moneda de la
        // proforma con el tipo de cambio, que queda guardado (y se respeta al convertirla en venta).
        List<Producto> productos = request.items().stream()
                .map(i -> productoService.obtenerPorId(i.productoId())).toList();
        List<java.math.BigDecimal> descuentos = request.items().stream()
                .map(i -> topeDescuentoService.validar(i.descuentoPorcentaje())).toList();
        String moneda = request.moneda().name();
        boolean requiereConversion = productos.stream().anyMatch(p -> !p.getMoneda().name().equals(moneda));
        java.math.BigDecimal tc = requiereConversion
                ? tipoCambioService.resolver(request.tipoCambio())
                : tipoCambioService.resolverSiSePuede(request.tipoCambio());
        proforma.setTipoCambio(tc);

        for (int i = 0; i < productos.size(); i++) {
            Producto producto = productos.get(i);
            java.math.BigDecimal precio = TipoCambioService.convertir(producto.calcularPrecioVenta(),
                    producto.getMoneda().name(), moneda, tc);
            proforma.agregarItem(new ItemProforma(producto, request.items().get(i).cantidad(), precio, descuentos.get(i)));
        }

        return ProformaResponseDTO.desdeEntidad(proformaRepository.save(proforma));
    }

    /**
     * Resultado de enviar una proforma, para mostrarlo en pantalla.
     * whatsapp: SIMULADO | SIN_TELEFONO | NO_SOLICITADO · correo: ENVIADO | FALLO | SIN_CORREO | NO_SOLICITADO
     */
    public record ResultadoEnvio(String whatsapp, String correo, String mensaje) {}

    /** RF confirmado: Lisandro envía proformas solo por WhatsApp y correo */
    public ResultadoEnvio enviar(Long proformaId, boolean porWhatsApp, boolean porCorreo) {
        Proforma proforma = obtenerOFallar(proformaId);
        if (proforma.getEstado() != Proforma.EstadoProforma.PENDIENTE
                && proforma.getEstado() != Proforma.EstadoProforma.ACEPTADA) {
            throw new ReglaNegocioException("No se puede enviar la proforma #" + proformaId
                    + ": está " + proforma.getEstado() + ".");
        }
        String resumen = "Proforma #" + proforma.getId() + " por un total de "
                + proforma.calcularTotal() + " " + proforma.getMoneda();
        String telefono = proforma.getCliente().getTelefono();
        String correo = proforma.getCliente().getCorreo();

        String estadoWhatsApp = "NO_SOLICITADO";
        if (porWhatsApp) {
            if (telefono == null || telefono.isBlank()) {
                estadoWhatsApp = "SIN_TELEFONO";
            } else {
                // Aún simulado: solo queda en el registro (ver auditoría B1)
                notificadorWhatsApp.enviarWhatsApp(telefono, resumen);
                proforma.setEnviadoWhatsApp(true);
                estadoWhatsApp = "SIMULADO";
            }
        }

        String estadoCorreo = "NO_SOLICITADO";
        if (porCorreo) {
            if (correo == null || correo.isBlank()) {
                estadoCorreo = "SIN_CORREO";
            } else {
                String cuerpoHtml = generarHtmlProforma(proforma);
                java.io.File pdfAdjunto = null;
                boolean enviado;
                try {
                    pdfAdjunto = generadorPdfService.generarProformaPdf(proforma);
                    enviado = notificadorCorreo.enviarCorreoConAdjunto(correo,
                            "Proforma JD Refrigeración S.A.C.", cuerpoHtml, pdfAdjunto);
                } catch (Exception e) {
                    System.err.println("Error generando PDF: " + e.getMessage());
                    // Fallback sin PDF
                    enviado = notificadorCorreo.enviarCorreo(correo, "Proforma JD Refrigeración S.A.C.", cuerpoHtml);
                } finally {
                    if (pdfAdjunto != null && pdfAdjunto.exists()) {
                        pdfAdjunto.delete(); // Limpiar archivo temporal
                    }
                }
                if (enviado) {
                    proforma.setEnviadoCorreo(true);
                }
                estadoCorreo = enviado ? "ENVIADO" : "FALLO";
            }
        }
        proformaRepository.save(proforma);
        return new ResultadoEnvio(estadoWhatsApp, estadoCorreo,
                mensajeEnvio(proforma.getId(), estadoWhatsApp, estadoCorreo, telefono, correo));
    }

    private static String mensajeEnvio(Long id, String whatsapp, String correo, String telefono, String email) {
        String w = switch (whatsapp) {
            case "SIMULADO" -> "WhatsApp a " + telefono + " registrado (el envío real por WhatsApp aún no está activo)";
            case "SIN_TELEFONO" -> "WhatsApp no enviado: el cliente no tiene teléfono";
            default -> null;
        };
        String c = switch (correo) {
            case "ENVIADO" -> "correo enviado a " + email;
            case "FALLO" -> "el correo a " + email + " NO se pudo enviar (revise la configuración de correo)";
            case "SIN_CORREO" -> "correo no enviado: el cliente no tiene correo";
            default -> null;
        };
        return "Proforma #" + id + ": " + java.util.stream.Stream.of(w, c)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.joining("; ")) + ".";
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
              .append(item.getDescuentoPorcentaje().signum() > 0
                      ? "<span style='display: block; font-size: 12px; color: #7fd18b;'>Descuento "
                        + item.getDescuentoPorcentaje().stripTrailingZeros().toPlainString() + " %</span>"
                      : "")
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
    public ProformaResponseDTO aceptar(Long proformaId) {
        Proforma proforma = obtenerOFallar(proformaId);
        if (proforma.getEstado() != Proforma.EstadoProforma.PENDIENTE) {
            throw new ReglaNegocioException("Solo se acepta una proforma PENDIENTE. La #" + proformaId
                    + " está " + proforma.getEstado() + ".");
        }
        validarNoVencida(proforma);
        proforma.setEstado(Proforma.EstadoProforma.ACEPTADA);
        return ProformaResponseDTO.desdeEntidad(proformaRepository.save(proforma));
    }

    /** El cliente no la acepta (o se echa atrás antes de convertirla en venta). */
    public ProformaResponseDTO rechazar(Long proformaId) {
        Proforma proforma = obtenerOFallar(proformaId);
        if (proforma.getEstado() != Proforma.EstadoProforma.PENDIENTE
                && proforma.getEstado() != Proforma.EstadoProforma.ACEPTADA) {
            throw new ReglaNegocioException("No se puede rechazar la proforma #" + proformaId
                    + ": está " + proforma.getEstado() + ".");
        }
        proforma.setEstado(Proforma.EstadoProforma.RECHAZADA);
        return ProformaResponseDTO.desdeEntidad(proformaRepository.save(proforma));
    }

    /**
     * Marca como VENCIDA toda proforma PENDIENTE cuya fecha de vencimiento ya pasó.
     * La ejecuta ProformaScheduledService todos los días.
     *
     * @return cuántas proformas se vencieron
     */
    @org.springframework.transaction.annotation.Transactional
    public int marcarVencidas() {
        List<Proforma> vencidas = proformaRepository
                .findByEstadoAndFechaVencimientoBefore(
                        Proforma.EstadoProforma.PENDIENTE, LocalDate.now());
        for (Proforma c : vencidas) {
            c.setEstado(Proforma.EstadoProforma.VENCIDA);
        }
        proformaRepository.saveAll(vencidas);
        return vencidas.size();
    }

    private void validarNoVencida(Proforma proforma) {
        if (proforma.getFechaVencimiento() != null
                && proforma.getFechaVencimiento().isBefore(LocalDate.now())) {
            throw new IllegalStateException(
                    "La proforma #" + proforma.getId() + " ya venció y no puede aceptarse");
        }
    }

    /** Fachada para Ventas: la proforma ya se convirtió en la venta indicada. */
    public void marcarConvertida(Long proformaId, Long ventaId) {
        Proforma proforma = obtenerOFallar(proformaId);
        proforma.setEstado(Proforma.EstadoProforma.CONVERTIDA);
        proforma.setVentaId(ventaId);
        proformaRepository.save(proforma);
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
