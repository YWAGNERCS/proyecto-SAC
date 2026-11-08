package com.bomerp.facturacion.facturacion.service;
import com.bomerp.facturacion.comprobante.dto.ComprobanteResponseDTO;
import com.bomerp.facturacion.notacredito.dto.NotaCreditoResponseDTO;
import com.bomerp.facturacion.serie.service.CorrelativoService;
import com.bomerp.facturacion.pdf.service.GeneradorPdfFacturacionService;
import com.bomerp.facturacion.comprobante.entity.Comprobante;
import com.bomerp.facturacion.comprobante.repository.ComprobanteRepository;
import com.bomerp.facturacion.comprobante.entity.EnvioSunatRegistro;
import com.bomerp.facturacion.comprobante.repository.EnvioSunatRegistroRepository;
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
    private final EnvioSunatRegistroRepository registrosEnvio;
    private final com.bomerp.facturacion.ose.integration.OseClient ose;
    private final org.springframework.transaction.support.TransactionTemplate tx;

    /**
     * Series de SGE-JD (app.facturacion.series.*). Si JD ya emite con F001/B001 desde el portal de
     * SUNAT u otro sistema, aquí van otras (p. ej. F002): un mismo número no puede emitirse dos veces.
     */
    @org.springframework.beans.factory.annotation.Value("${app.facturacion.series.factura:F001}")
    private String serieFactura;
    @org.springframework.beans.factory.annotation.Value("${app.facturacion.series.boleta:B001}")
    private String serieBoleta;
    @org.springframework.beans.factory.annotation.Value("${app.facturacion.series.nota-factura:FC01}")
    private String serieNotaFactura;
    @org.springframework.beans.factory.annotation.Value("${app.facturacion.series.nota-boleta:BC01}")
    private String serieNotaBoleta;

    public FacturacionService(ComprobanteRepository comprobanteRepository, VentaService ventaService, EnvioSunatService envioSunat, com.bomerp.notificaciones.notificacion.service.NotificadorCorreo notificadorCorreo, GeneradorPdfFacturacionService generadorPdfFacturacionService, NotaCreditoRepository notaCreditoRepository,
                              CorrelativoService correlativoService, EnvioSunatRegistroRepository registrosEnvio,
                              com.bomerp.facturacion.ose.integration.OseClient ose,
                              org.springframework.transaction.PlatformTransactionManager txManager) {
        this.comprobanteRepository = comprobanteRepository;
        this.ventaService = ventaService;
        this.envioSunat = envioSunat;
        this.notificadorCorreo = notificadorCorreo;
        this.generadorPdfFacturacionService = generadorPdfFacturacionService;
        this.notaCreditoRepository = notaCreditoRepository;
        this.correlativoService = correlativoService;
        this.registrosEnvio = registrosEnvio;
        this.ose = ose;
        this.tx = new org.springframework.transaction.support.TransactionTemplate(txManager);
    }

    @Transactional
    public Comprobante emitirComprobanteParaVenta(Long ventaId) {
        Venta venta = ventaService.obtenerPorIdPublico(ventaId);   // 404 si no existe

        // Una venta tiene un solo comprobante vigente (el rechazado con CDR se sustituye con emitirReemplazo)
        if (comprobanteRepository.findByVentaIdAndReemplazadoFalse(ventaId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La venta ya tiene un comprobante emitido.");
        }
        String motivo = ose.motivoParaNoEmitir(venta);
        if (motivo != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Comprobante de la venta #" + ventaId + " por emitir: " + motivo);
        }
        return crearComprobante(venta, null);
    }

    /**
     * Al registrar la venta (VentasFacturacionListener). Si falta un dato de JD para emitir el comprobante
     * (p. ej. la cuenta de detracciones), la venta se guarda igual y queda "por emitir", sin número: así no
     * consume un correlativo ni corre el plazo de 3 días para enviarlo (informe de rechazos SUNAT, D-6).
     * emitirVentasPorEmitir lo emite cuando el dato ya está.
     *
     * @return el comprobante creado, o vacío si quedó por emitir
     */
    @Transactional
    public Optional<Comprobante> emitirAlRegistrar(Long ventaId) {
        Venta venta = ventaService.obtenerPorIdPublico(ventaId);
        String motivo = ose.motivoParaNoEmitir(venta);
        if (motivo != null) {
            log.warn("Venta #{} registrada sin comprobante (por emitir): {}", ventaId, motivo);
            return Optional.empty();
        }
        return Optional.of(emitirComprobanteParaVenta(ventaId));
    }

    /**
     * Emite el comprobante de las ventas que quedaron por emitir, en cuanto se puede (p. ej. al arrancar con la
     * cuenta de detracciones ya configurada). Cada una en su propia transacción: una que falle no frena a las demás.
     */
    @org.springframework.scheduling.annotation.Scheduled(initialDelayString = "${app.facturacion.reintento-inicial-ms:60000}",
            fixedDelayString = "${app.facturacion.reintento-ms:180000}")
    public void emitirVentasPorEmitir() {
        List<Long> listas = tx.execute(s -> comprobanteRepository.ventasPorEmitir().stream()
                .filter(v -> ose.motivoParaNoEmitir(v) == null).map(Venta::getId).toList());
        if (listas == null || listas.isEmpty()) return;
        log.info("Emitiendo el comprobante de {} venta(s) que estaban por emitir", listas.size());
        for (Long ventaId : listas) {
            try {
                tx.executeWithoutResult(s -> {
                    Comprobante c = emitirComprobanteParaVenta(ventaId);
                    log.info("Venta #{}: comprobante {}-{} emitido (estaba por emitir)", ventaId, c.getSerie(), c.getNumero());
                });
            } catch (Exception e) {
                log.error("No se pudo emitir el comprobante de la venta #{} (por emitir): {}", ventaId, e.getMessage());
            }
        }
    }

    /**
     * Comprobante de reemplazo (informe de rechazos SUNAT, D-2 y D-4): SUNAT rechazó el comprobante con
     * constancia (CDR), su número quedó usado y no puede reenviarse. Se emite otro, con número nuevo, fecha
     * de hoy y los datos actuales del cliente (ya corregidos), para la MISMA venta. No se tocan el stock, el
     * cobro ni los mantenimientos de la venta: solo cambia el documento tributario. El rechazado queda como
     * historial, marcado como reemplazado, con su CDR.
     *
     * También para uno que pasó su plazo de envío (3 días, D-6) sin que SUNAT lo registrara: enviarlo ya no le
     * daría validez. Ese número nunca llegó a SUNAT.
     *
     * 409 si el comprobante no tiene un rechazo definitivo ni está fuera de plazo (válido, pendiente o una
     * excepción que todavía se corrige reenviando el mismo número), si ya fue reemplazado o si la venta está
     * anulada (ya no necesita comprobante).
     */
    @Transactional
    public Comprobante emitirReemplazo(Long comprobanteId) {
        Comprobante rechazado = comprobanteRepository.bloquear(comprobanteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comprobante no encontrado: " + comprobanteId));
        String numero = rechazado.getSerie() + "-" + Integer.parseInt(rechazado.getNumero());
        if (rechazado.isReemplazado()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, numero + " ya fue reemplazado por otro comprobante.");
        }
        boolean fueraDePlazo = rechazado.fueraDePlazo(java.time.LocalDate.now());
        if (!rechazado.rechazoDefinitivo() && !fueraDePlazo) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, rechazado.getEstadoSunat().valido()
                    ? numero + " tiene validez ante SUNAT: no se reemplaza. Para anularlo, anule la venta (se emite una nota de crédito)."
                    : numero + " no fue rechazado con constancia de SUNAT: no se reemplaza. Si SUNAT no lo recibió, corrija el dato y reenvíelo con el mismo número.");
        }
        Venta venta = rechazado.getVenta();
        if (venta.getEstado() == Venta.EstadoVenta.ANULADA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La venta #" + venta.getId()
                    + " está anulada: ya no necesita comprobante.");
        }
        String motivo = ose.motivoParaNoEmitir(venta);
        if (motivo != null) {
            // Un reemplazo con número nuevo tampoco podría enviarse: se gastaría otro número
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Todavía no se puede emitir el reemplazo de " + numero + ": " + motivo);
        }
        rechazado.setReemplazado(true);
        comprobanteRepository.saveAndFlush(rechazado);   // antes del nuevo: el índice admite un solo vigente por venta

        Comprobante nuevo = crearComprobante(venta, rechazado);
        log.info("{} ({}) reemplazado por {}-{} en la venta #{}", numero,
                fueraDePlazo ? "fuera del plazo de envío" : "rechazado por SUNAT, código " + rechazado.getEnvio().getCodigo(),
                nuevo.getSerie(), nuevo.getNumero(), venta.getId());
        return nuevo;
    }

    /** Crea el comprobante PENDIENTE de la venta con los datos actuales del cliente y lo programa para SUNAT. */
    private Comprobante crearComprobante(Venta venta, Comprobante reemplazaA) {
        Comprobante comprobante = new Comprobante();
        comprobante.setVenta(venta);
        comprobante.setReemplazaA(reemplazaA);
        comprobante.setTotal(venta.calcularTotal());
        comprobante.fijarReceptor(venta.getCliente());

        if (venta.getCliente().getTipo() == Cliente.TipoCliente.EMPRESA) {
            comprobante.setTipo(Comprobante.TipoComprobante.FACTURA);
            comprobante.setSerie(serieFactura);
        } else {
            comprobante.setTipo(Comprobante.TipoComprobante.BOLETA);
            comprobante.setSerie(serieBoleta);
        }

        // Correlativo por serie: 00000001, 00000002, ... sin saltos ni repetidos (SUNAT)
        comprobante.setNumero(correlativoService.siguiente(comprobante.getSerie()));

        Comprobante comprobanteGuardado = comprobanteRepository.save(comprobante);
        ventaService.asignarComprobante(venta.getId(), comprobanteGuardado.getId());

        // Queda PENDIENTE: se envía a SUNAT cuando la transacción se confirme (EnvioSunatService), así
        // una SUNAT lenta o caída no traba ni deshace la venta; si no responde, se reintenta solo.
        envioSunat.programarComprobante(comprobanteGuardado.getId());
        return comprobanteGuardado;
    }

    /** Reemplazo desde la API: responde con el DTO del comprobante nuevo. */
    @Transactional
    public ComprobanteResponseDTO reemplazar(Long comprobanteId) {
        return ComprobanteResponseDTO.desdeEntidad(emitirReemplazo(comprobanteId));
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
        Optional<Comprobante> comprobante = comprobanteRepository.findByVentaIdAndReemplazadoFalse(ventaId);
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
        nota.setSerie(original.getTipo() == Comprobante.TipoComprobante.FACTURA ? serieNotaFactura : serieNotaBoleta);
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
        return comprobanteRepository.findByVentaIdAndReemplazadoFalse(ventaId).map(c -> c.getSerie() + "-" + c.getNumero());
    }

    /** Número de la nota de crédito que anuló el comprobante de una venta, si existe. */
    @Override
    @Transactional(readOnly = true)
    public Optional<String> numeroNotaCreditoDeVenta(Long ventaId) {
        return comprobanteRepository.findByVentaIdAndReemplazadoFalse(ventaId)
                .flatMap(c -> notaCreditoRepository.findByComprobanteReferencia_Id(c.getId()))
                .map(nc -> nc.getSerie() + "-" + nc.getNumero());
    }

    /** PDF del comprobante (factura o boleta) de una venta, para descargarlo. 404 si la venta no tiene comprobante. */
    @Transactional(readOnly = true)
    public PdfComprobante pdfDeVenta(Long ventaId) {
        Comprobante comprobante = comprobanteRepository.findByVentaIdAndReemplazadoFalse(ventaId)
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
        List<Comprobante> lista = comprobanteRepository.findByVenta_IdInOrderByIdAsc(ventaIds.stream().limit(200).toList());
        var notas = notaCreditoRepository.findByComprobanteReferencia_IdIn(lista.stream().map(Comprobante::getId).toList())
                .stream().collect(java.util.stream.Collectors.toMap(n -> n.getComprobanteReferencia().getId(), n -> n));
        // Por venta: el vigente y, si hubo reemplazos, los rechazados anteriores (historial de emisiones)
        var reemplazados = lista.stream().filter(Comprobante::isReemplazado)
                .collect(java.util.stream.Collectors.groupingBy(c -> c.getVenta().getId()));
        List<com.bomerp.facturacion.comprobante.dto.EstadoSunatVentaDTO> estados = new java.util.ArrayList<>(lista.stream()
                .filter(c -> !c.isReemplazado())
                .map(c -> com.bomerp.facturacion.comprobante.dto.EstadoSunatVentaDTO.de(c, notas.get(c.getId()),
                        reemplazados.getOrDefault(c.getVenta().getId(), List.of())))
                .toList());
        // Ventas sin comprobante todavía (esperan un dato de JD): se muestran como "por emitir"
        var pedidas = new java.util.HashSet<>(ventaIds);
        comprobanteRepository.ventasPorEmitir().stream().filter(v -> pedidas.contains(v.getId()))
                .forEach(v -> estados.add(com.bomerp.facturacion.comprobante.dto.EstadoSunatVentaDTO.porEmitir(v.getId(),
                        java.util.Objects.requireNonNullElse(ose.motivoParaNoEmitir(v), "Se emite en unos minutos."))));
        return estados;
    }

    /**
     * Para la campana: facturas y boletas de ventas vigentes que SUNAT todavía no registró, según su plazo de
     * envío de 3 días (informe D-6). Por vencer: vence hoy o mañana. Fuera de plazo: ya venció, hay que
     * emitir un comprobante de reemplazo.
     */
    public record PlazoEnvioDTO(int porVencer, int fueraDePlazo, List<String> numerosPorVencer, List<String> numerosFueraDePlazo,
                                int porEmitir, List<Long> ventasPorEmitir) {}

    @Transactional(readOnly = true)
    public PlazoEnvioDTO plazoDeEnvio() {
        java.time.LocalDate hoy = java.time.LocalDate.now();
        List<String> porVencer = new java.util.ArrayList<>();
        List<String> fuera = new java.util.ArrayList<>();
        comprobanteRepository.findByReemplazadoFalseAndEstadoSunatIn(
                        List.of(Comprobante.EstadoSunat.PENDIENTE, Comprobante.EstadoSunat.RECHAZADO)).stream()
                .filter(Comprobante::faltaQueSunatLoRegistre)
                .filter(c -> c.getVenta() == null || c.getVenta().getEstado() != Venta.EstadoVenta.ANULADA)
                .sorted(java.util.Comparator.comparing(Comprobante::getFechaEmision).thenComparing(Comprobante::getId))
                .forEach(c -> {
                    String numero = c.getSerie() + "-" + Integer.parseInt(c.getNumero());
                    if (c.fueraDePlazo(hoy)) fuera.add(numero);
                    else if (!hoy.isBefore(c.fechaLimiteEnvio().minusDays(1))) porVencer.add(numero);
                });
        // Ventas sin comprobante porque falta un dato de JD: no corre plazo, pero el cliente espera su comprobante
        List<Long> porEmitir = comprobanteRepository.ventasPorEmitir().stream()
                .filter(v -> ose.motivoParaNoEmitir(v) != null).map(Venta::getId).toList();
        return new PlazoEnvioDTO(porVencer.size(), fuera.size(), porVencer, fuera, porEmitir.size(), porEmitir);
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

    /** Una respuesta de SUNAT del historial de envíos (sin el XML ni el CDR: esos se descargan aparte). */
    public record EnvioSunatDTO(Long id, int intento, java.time.LocalDateTime fecha, String resultado, Integer codigo,
                                String mensaje, boolean tieneXml, boolean tieneCdr) {
        static EnvioSunatDTO de(EnvioSunatRegistro r) {
            return new EnvioSunatDTO(r.getId(), r.getIntento(), r.getFecha(), r.getResultado(), r.getCodigo(),
                    r.getMensaje(), r.tieneXml(), r.tieneCdr());
        }
    }

    /** Historial de envíos a SUNAT de un comprobante o de una nota de crédito, el más reciente primero. */
    @Transactional(readOnly = true)
    public List<EnvioSunatDTO> historialEnvios(boolean esNota, Long id) {
        if (esNota ? !notaCreditoRepository.existsById(id) : !comprobanteRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, (esNota ? "Nota de crédito" : "Comprobante") + " no encontrado: " + id);
        }
        var registros = esNota ? registrosEnvio.findByNotaCreditoIdOrderByIdDesc(id) : registrosEnvio.findByComprobanteIdOrderByIdDesc(id);
        return registros.stream().map(EnvioSunatDTO::de).toList();
    }

    /** XML o CDR de un envío del historial (p. ej. la constancia de un rechazo que después se reenvió). */
    @Transactional(readOnly = true)
    public ArchivoSunat archivoDeEnvio(Long envioId, boolean cdr) {
        EnvioSunatRegistro r = registrosEnvio.findById(envioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Envío a SUNAT no encontrado: " + envioId));
        String numero;
        if (r.getComprobanteId() != null) {
            Comprobante c = comprobanteRepository.findById(r.getComprobanteId()).orElseThrow();
            numero = c.getSerie() + "-" + Integer.parseInt(c.getNumero());
        } else {
            NotaCredito n = notaCreditoRepository.findById(r.getNotaCreditoId()).orElseThrow();
            numero = n.getSerie() + "-" + Integer.parseInt(n.getNumero());
        }
        byte[] contenido = cdr ? r.getCdr() : r.getXml();
        if (contenido == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, numero + ": el intento " + r.getIntento()
                    + (cdr ? " no tiene constancia de SUNAT (CDR)." : " no tiene XML firmado."));
        }
        String sufijo = "_intento" + r.getIntento();
        return new ArchivoSunat(cdr ? "CDR_" + numero + sufijo + ".zip" : numero + sufijo + ".xml", contenido, cdr);
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
        String motivo = motivoParaNoEnviarAlCliente(comprobante);
        if (motivo != null) {
            log.info("Comprobante {}: no se envía por correo. {}", comprobanteId, motivo);
            return;
        }
        enviarFacturaCorreo(comprobanteId);
    }

    /**
     * Al cliente solo se le envía un comprobante con validez ante SUNAT (revisión externa F-01): el correo
     * dice "ha sido emitida". Uno PENDIENTE o RECHAZADO no la tiene; uno del modo simulado tampoco, aunque
     * figure ACEPTADO, porque nunca salió a SUNAT (el envío real siempre guarda el XML firmado; el simulado no).
     *
     * @return el motivo para no enviarlo, o null si se puede enviar
     */
    static String motivoParaNoEnviarAlCliente(Comprobante c) {
        String numero = c.getSerie() + "-" + c.getNumero();
        if (c.getEstadoSunat() == null || !c.getEstadoSunat().valido()) {
            return numero + " todavía no tiene validez ante SUNAT (estado " + c.getEstadoSunat()
                    + "): se podrá enviar al cliente cuando SUNAT lo acepte.";
        }
        if (c.getEnvio().getXmlFirmado() == null) {
            return numero + " se emitió en modo simulado (no se envió a SUNAT): no se envía al cliente como comprobante.";
        }
        return null;
    }

    /** Nombre del receptor tal como se emitió; en comprobantes sin esos datos, el del cliente actual. */
    private static String nombreReceptor(Comprobante c) {
        return c.getReceptorNombre() != null ? c.getReceptorNombre() : c.getVenta().getCliente().getNombreORazonSocial();
    }

    @Transactional(readOnly = true)
    public void enviarFacturaCorreo(Long comprobanteId) {
        Comprobante comprobante = comprobanteRepository.findById(comprobanteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comprobante no encontrado: " + comprobanteId));
        String motivo = motivoParaNoEnviarAlCliente(comprobante);
        if (motivo != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, motivo);
        }

        String correoDestino = comprobante.getVenta().getCliente().getCorreo();
        if (correoDestino == null || correoDestino.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El cliente no tiene correo registrado");
        }

        String tipoDoc = comprobante.getTipo() == Comprobante.TipoComprobante.FACTURA ? "Factura Electrónica" : "Boleta Electrónica";
        String numeroDoc = comprobante.getSerie() + "-" + comprobante.getNumero();
        
        String cuerpoHtml = "<!DOCTYPE html><html><body style='font-family: Arial, sans-serif; background-color: #f4f4f4; padding: 20px;'>" +
                            "<div style='max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 8px; padding: 30px; box-shadow: 0 4px 8px rgba(0,0,0,0.1);'>" +
                            "<h2 style='color: #0078d4;'>Documento Emitido: " + tipoDoc + "</h2>" +
                            "<p>Estimado cliente <strong>" + org.springframework.web.util.HtmlUtils.htmlEscape(nombreReceptor(comprobante)) + "</strong>,</p>" +
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
