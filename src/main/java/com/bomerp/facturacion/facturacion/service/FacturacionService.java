package com.bomerp.facturacion.facturacion.service;
import com.bomerp.facturacion.comprobante.dto.ComprobanteResponseDTO;
import com.bomerp.facturacion.notacredito.dto.NotaCreditoResponseDTO;
import com.bomerp.facturacion.serie.service.CorrelativoService;
import com.bomerp.facturacion.pdf.service.GeneradorPdfFacturacionService;
import com.bomerp.facturacion.comprobante.entity.Comprobante;
import com.bomerp.facturacion.comprobante.repository.ComprobanteRepository;
import com.bomerp.facturacion.notacredito.entity.NotaCredito;
import com.bomerp.facturacion.notacredito.repository.NotaCreditoRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

import com.bomerp.clientes.general.integration.Cliente;
import com.bomerp.ventas.venta.entity.Venta;
import com.bomerp.ventas.venta.service.ComprobantesDeVenta;
import com.bomerp.ventas.venta.service.VentaService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Slf4j
public class FacturacionService implements ComprobantesDeVenta {

    private final ComprobanteRepository comprobanteRepository;
    private final VentaService ventaService;
    private final EnvioSunatService envioSunat;
    private final com.bomerp.notificaciones.notificacion.service.NotificadorCorreo notificadorCorreo;
    private final GeneradorPdfFacturacionService generadorPdfFacturacionService;
    private final NotaCreditoRepository notaCreditoRepository;
    private final CorrelativoService correlativoService;

    public FacturacionService(ComprobanteRepository comprobanteRepository, VentaService ventaService, EnvioSunatService envioSunat, com.bomerp.notificaciones.notificacion.service.NotificadorCorreo notificadorCorreo, GeneradorPdfFacturacionService generadorPdfFacturacionService, NotaCreditoRepository notaCreditoRepository,
                              CorrelativoService correlativoService) {
        this.comprobanteRepository = comprobanteRepository;
        this.ventaService = ventaService;
        this.envioSunat = envioSunat;
        this.notificadorCorreo = notificadorCorreo;
        this.generadorPdfFacturacionService = generadorPdfFacturacionService;
        this.notaCreditoRepository = notaCreditoRepository;
        this.correlativoService = correlativoService;
    }

    @Transactional
    public Comprobante emitirComprobanteParaVenta(Long ventaId) {
        Venta venta = ventaService.obtenerPorIdPublico(ventaId);   // 404 si no existe

        // Verificamos si ya tiene comprobante
        if (comprobanteRepository.findByVentaId(ventaId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La venta ya tiene un comprobante emitido.");
        }

        Comprobante comprobante = new Comprobante();
        comprobante.setVenta(venta);
        comprobante.setTotal(venta.calcularTotal());

        // Lógica Simple (Patrón O - Open/Closed aplicable a futuro aquí)
        if (venta.getCliente().getTipo() == Cliente.TipoCliente.EMPRESA) {
            comprobante.setTipo(Comprobante.TipoComprobante.FACTURA);
            comprobante.setSerie("F001");
        } else {
            comprobante.setTipo(Comprobante.TipoComprobante.BOLETA);
            comprobante.setSerie("B001");
        }

        // Correlativo por serie: 00000001, 00000002, ... sin saltos ni repetidos (SUNAT)
        comprobante.setNumero(correlativoService.siguiente(comprobante.getSerie()));

        // Guardamos antes de enviar al OSE
        Comprobante comprobanteGuardado = comprobanteRepository.save(comprobante);

        // Actualizamos la venta
        ventaService.asignarComprobante(ventaId, comprobanteGuardado.getId());

        // Queda PENDIENTE: se envía a SUNAT cuando la venta se confirme (EnvioSunatService), así
        // una SUNAT lenta o caída no traba ni deshace la venta; si no responde, se reintenta solo.
        envioSunat.programarComprobante(comprobanteGuardado.getId());
        return comprobanteGuardado;
    }
    
    /**
     * Emite la nota de crédito que anula el comprobante de una venta.
     * Solo aplica si el comprobante fue ACEPTADO por SUNAT: uno pendiente o rechazado
     * no tiene validez tributaria y no necesita anularse.
     * Es idempotente: si el comprobante ya tiene nota de crédito, devuelve esa misma.
     *
     * @return la nota emitida, o vacío si la venta no tiene comprobante aceptado
     */
    @Transactional
    public Optional<NotaCredito> emitirNotaCreditoPorAnulacion(Long ventaId, String motivo) {
        Optional<Comprobante> comprobante = comprobanteRepository.findByVentaId(ventaId);
        // Un comprobante rechazado no tiene validez: no hay nada que anular ante SUNAT. Uno pendiente
        // sí: la nota se crea ya y sale cuando SUNAT acepte el original (EnvioSunatService).
        if (comprobante.isEmpty() || comprobante.get().getEstadoSunat() == Comprobante.EstadoSunat.RECHAZADO) {
            log.info("Venta {} sin comprobante válido: no requiere nota de crédito", ventaId);
            return Optional.empty();
        }
        Comprobante original = comprobante.get();

        Optional<NotaCredito> existente = notaCreditoRepository.findByComprobanteReferencia_Id(original.getId());
        if (existente.isPresent()) {
            return existente;
        }

        NotaCredito nota = new NotaCredito();
        nota.setComprobanteReferencia(original);
        nota.setMotivo(motivo);
        nota.setTotal(original.getTotal());
        nota.setSerie(original.getTipo() == Comprobante.TipoComprobante.FACTURA ? "FC01" : "BC01");
        nota.setNumero(correlativoService.siguiente(nota.getSerie()));
        NotaCredito guardada = notaCreditoRepository.save(nota);

        envioSunat.programarNota(guardada.getId());
        log.info("Nota de crédito {}-{} registrada: anula {} (se envía a SUNAT en segundo plano)",
                guardada.getSerie(), guardada.getNumero(), guardada.getNumeroComprobanteReferencia());
        return Optional.of(guardada);
    }

    /** Fachada para Ventas (reportes y Excel): número "SERIE-NUMERO" del comprobante de una venta. */
    @Override
    @Transactional(readOnly = true)
    public Optional<String> numeroComprobanteDeVenta(Long ventaId) {
        return comprobanteRepository.findByVentaId(ventaId).map(c -> c.getSerie() + "-" + c.getNumero());
    }

    /** Número de la nota de crédito que anuló el comprobante de una venta, si existe. */
    @Override
    @Transactional(readOnly = true)
    public Optional<String> numeroNotaCreditoDeVenta(Long ventaId) {
        return comprobanteRepository.findByVentaId(ventaId)
                .flatMap(c -> notaCreditoRepository.findByComprobanteReferencia_Id(c.getId()))
                .map(nc -> nc.getSerie() + "-" + nc.getNumero());
    }

    /** PDF del comprobante (factura o boleta) de una venta, para descargarlo. 404 si la venta no tiene comprobante. */
    @Transactional(readOnly = true)
    public PdfComprobante pdfDeVenta(Long ventaId) {
        Comprobante comprobante = comprobanteRepository.findByVentaId(ventaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "La venta #" + ventaId + " no tiene comprobante emitido."));
        java.io.File archivo = null;
        try {
            archivo = generadorPdfFacturacionService.generarFacturaPdf(comprobante);
            String nombre = comprobante.getTipo().name() + "_" + comprobante.getSerie() + "-" + comprobante.getNumero() + ".pdf";
            return new PdfComprobante(nombre, java.nio.file.Files.readAllBytes(archivo.toPath()));
        } catch (Exception e) {
            throw new RuntimeException("No se pudo generar el PDF del comprobante: " + e.getMessage(), e);
        } finally {
            if (archivo != null) archivo.delete();   // archivo temporal
        }
    }

    public record PdfComprobante(String nombreArchivo, byte[] contenido) {}

    /** Estado ante SUNAT del comprobante (y su nota de crédito) de cada venta pedida; las que no tienen comprobante no aparecen. */
    @Transactional(readOnly = true)
    public List<com.bomerp.facturacion.comprobante.dto.EstadoSunatVentaDTO> estadosDeVentas(List<Long> ventaIds) {
        if (ventaIds == null || ventaIds.isEmpty()) return List.of();
        List<Comprobante> lista = comprobanteRepository.findByVenta_IdIn(ventaIds.stream().limit(200).toList());
        var notas = notaCreditoRepository.findByComprobanteReferencia_IdIn(lista.stream().map(Comprobante::getId).toList())
                .stream().collect(java.util.stream.Collectors.toMap(n -> n.getComprobanteReferencia().getId(), n -> n));
        return lista.stream().map(c -> com.bomerp.facturacion.comprobante.dto.EstadoSunatVentaDTO.de(c, notas.get(c.getId()))).toList();
    }

    /** Archivo guardado del envío a SUNAT: XML firmado o CDR (constancia de recepción, ZIP). */
    public record ArchivoSunat(String nombre, byte[] contenido, boolean esZip) {}

    @Transactional(readOnly = true)
    public ArchivoSunat archivoSunat(boolean esNota, Long id, boolean cdr) {
        String numero;
        com.bomerp.facturacion.comprobante.entity.EnvioSunat envio;
        if (esNota) {
            NotaCredito n = notaCreditoRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nota de crédito no encontrada: " + id));
            numero = n.getSerie() + "-" + Integer.parseInt(n.getNumero());
            envio = n.getEnvio();
        } else {
            Comprobante c = comprobanteRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comprobante no encontrado: " + id));
            numero = c.getSerie() + "-" + Integer.parseInt(c.getNumero());
            envio = c.getEnvio();
        }
        byte[] contenido = cdr ? envio.getCdr() : envio.getXmlFirmado();
        if (contenido == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, cdr
                    ? numero + " todavía no tiene constancia de SUNAT (CDR)."
                    : numero + " todavía no tiene XML firmado (no se ha enviado a SUNAT).");
        }
        return new ArchivoSunat(cdr ? "CDR_" + numero + ".zip" : numero + ".xml", contenido, cdr);
    }

    @Transactional(readOnly = true)
    public List<NotaCreditoResponseDTO> listarNotasCredito() {
        return notaCreditoRepository.findAllByOrderByIdDesc().stream().map(NotaCreditoResponseDTO::desdeEntidad).toList();
    }

    /** Emisión manual desde la API: responde con el DTO (la entidad lleva la venta completa). */
    @Transactional
    public ComprobanteResponseDTO emitir(Long ventaId) {
        return ComprobanteResponseDTO.desdeEntidad(emitirComprobanteParaVenta(ventaId));
    }

    /** Tras la aceptación de SUNAT: envía el comprobante por correo solo si el cliente tiene uno. */
    @Transactional(readOnly = true)
    public void enviarFacturaCorreoSiTieneCorreo(Long comprobanteId) {
        Comprobante comprobante = comprobanteRepository.findById(comprobanteId).orElse(null);
        String correo = comprobante == null ? null : comprobante.getVenta().getCliente().getCorreo();
        if (correo == null || correo.isBlank()) {
            log.info("Comprobante {} aceptado; no se envía por correo: el cliente no tiene correo", comprobanteId);
            return;
        }
        enviarFacturaCorreo(comprobanteId);
    }

    @Transactional(readOnly = true)
    public void enviarFacturaCorreo(Long comprobanteId) {
        Comprobante comprobante = comprobanteRepository.findById(comprobanteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comprobante no encontrado: " + comprobanteId));
        
        String correoDestino = comprobante.getVenta().getCliente().getCorreo();
        if (correoDestino == null || correoDestino.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El cliente no tiene correo registrado");
        }

        String tipoDoc = comprobante.getTipo() == Comprobante.TipoComprobante.FACTURA ? "Factura Electrónica" : "Boleta Electrónica";
        String numeroDoc = comprobante.getSerie() + "-" + comprobante.getNumero();
        
        String cuerpoHtml = "<!DOCTYPE html><html><body style='font-family: Arial, sans-serif; background-color: #f4f4f4; padding: 20px;'>" +
                            "<div style='max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 8px; padding: 30px; box-shadow: 0 4px 8px rgba(0,0,0,0.1);'>" +
                            "<h2 style='color: #0078d4;'>Documento Emitido: " + tipoDoc + "</h2>" +
                            "<p>Estimado cliente <strong>" + comprobante.getVenta().getCliente().getNombreORazonSocial() + "</strong>,</p>" +
                            "<p>Le informamos que se ha emitido su comprobante de pago <strong>" + numeroDoc + "</strong> por el monto de <strong>" + comprobante.getVenta().getMoneda() + " " + comprobante.getTotal() + "</strong>.</p>" +
                            "<p>Adjunto a este correo encontrará la representación impresa de su documento (PDF).</p>" +
                            "<hr style='border: none; border-top: 1px solid #eee; margin: 20px 0;'/>" +
                            "<p style='color: #888; font-size: 12px;'>Este documento es una representación impresa de un comprobante de pago electrónico.</p>" +
                            "</div></body></html>";
        
        java.io.File pdfAdjunto = null;
        try {
            pdfAdjunto = generadorPdfFacturacionService.generarFacturaPdf(comprobante);
            notificadorCorreo.enviarCorreoConAdjunto(correoDestino, "Su " + tipoDoc + " " + numeroDoc + " ha sido emitida", cuerpoHtml, pdfAdjunto);
        } catch (Exception e) {
            log.error("Error generando PDF de factura: {}", e.getMessage(), e);
            // Fallback sin adjunto si falla la generación
            notificadorCorreo.enviarCorreo(correoDestino, "Su " + tipoDoc + " " + numeroDoc + " ha sido emitida", cuerpoHtml);
        } finally {
            if (pdfAdjunto != null && pdfAdjunto.exists()) {
                pdfAdjunto.delete();
            }
        }
    }
}
