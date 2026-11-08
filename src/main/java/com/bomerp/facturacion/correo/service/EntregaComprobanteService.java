package com.bomerp.facturacion.correo.service;

import com.bomerp.config.empresa.EmpresaProperties;
import com.bomerp.facturacion.comprobante.entity.Comprobante;
import com.bomerp.facturacion.comprobante.repository.ComprobanteRepository;
import com.bomerp.facturacion.correo.dto.EntregaCorreoDTO;
import com.bomerp.facturacion.correo.entity.EnvioCorreo;
import com.bomerp.facturacion.correo.entity.EnvioCorreo.Estado;
import com.bomerp.facturacion.correo.entity.IntentoCorreo;
import com.bomerp.facturacion.correo.repository.EnvioCorreoRepository;
import com.bomerp.facturacion.correo.repository.IntentoCorreoRepository;
import com.bomerp.facturacion.pdf.service.GeneradorPdfFacturacionService;
import com.bomerp.notificaciones.notificacion.service.NotificadorCorreo;
import com.bomerp.notificaciones.notificacion.service.NotificadorCorreo.Adjunto;
import com.bomerp.notificaciones.notificacion.service.NotificadorCorreo.MensajeCorreo;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Entrega del comprobante electrónico (factura o boleta) por correo al cliente, con el PDF y el XML firmado.
 *
 * Flujo: SUNAT acepta el comprobante → {@link #solicitarAutomatico} deja una solicitud PENDIENTE en Oracle → un
 * trabajador la toma ({@link #procesar}), arma los adjuntos, la entrega al servidor SMTP y guarda el resultado.
 *
 * Garantías:
 * - Solo se envían comprobantes con validez tributaria: aceptados u observados por SUNAT y emitidos en el ambiente de
 *   PRODUCCION, según el ambiente que quedó registrado en el propio comprobante al aceptarlo (no según la configuración
 *   de hoy, que puede haber cambiado). Nunca pendientes, rechazados, simulados ni aceptados por la beta.
 * - Si SUNAT acepta y el backend se cae antes de crear la solicitud, la conciliación periódica lo detecta: busca
 *   comprobantes aceptados en producción, con cliente con correo y sin envío automático, y lo crea.
 * - Una sola solicitud automática por comprobante (índice único en Oracle): aceptarlo dos veces no manda dos correos.
 *   Y ninguna automática si el comprobante ya tiene CUALQUIER envío (p. ej. un reenvío manual): ni el evento tardío ni
 *   la conciliación lo duplican.
 * - El reenvío manual cancela, en la misma transacción, el reintento automático que estuviera programado; y un
 *   reintento automático nunca sale si el comprobante ya se entregó (o quedó por confirmar) en otro envío.
 * - Un envío a la vez por comprobante: tomarlo bloquea la fila del comprobante y cambia el estado con un UPDATE
 *   condicional, así dos trabajadores (o dos backends) nunca lo mandan juntos.
 * - Ninguna transacción queda abierta mientras se habla con el servidor SMTP: tomar, enviar y registrar son pasos aparte.
 * - Si falla, queda FALLIDO con la hora del próximo intento (2, 10, 30 y 120 minutos; máximo app.correo.max-intentos);
 *   la revisión periódica también retoma lo pendiente al reiniciar el backend. Nada de esto toca la venta, el
 *   comprobante, su número, el stock ni la cobranza.
 * - Política ante la duda: el Message-ID se guarda ANTES de enviar. Si el proceso se cae entre que el servidor SMTP
 *   acepta y que Oracle lo registra, el envío queda EN_PROCESO; pasado app.correo.minutos-en-proceso pasa a INCIERTO
 *   y NO se reenvía solo (SMTP no permite garantizar "exactamente una vez": se prefiere no duplicar). Lisandro lo
 *   revisa y, si al cliente no le llegó, usa "Reenviar por correo".
 */
@Service
@Slf4j
public class EntregaComprobanteService {

    /** Esperas entre intentos automáticos: después del 1.º, del 2.º, del 3.º y del 4.º. */
    static final Duration[] ESPERAS = {Duration.ofMinutes(2), Duration.ofMinutes(10), Duration.ofMinutes(30), Duration.ofMinutes(120)};

    private static final Pattern CORREO = Pattern.compile("^[A-Za-z0-9._%+'-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final EnvioCorreoRepository envios;
    private final IntentoCorreoRepository intentos;
    private final ComprobanteRepository comprobantes;
    private final NotificadorCorreo notificador;
    private final GeneradorPdfFacturacionService generadorPdf;
    private final EmpresaProperties empresa;
    private final TransactionTemplate tx;
    private final int maxIntentos;
    private final long minutosEnProceso;
    private final long diasConciliacion;
    private final String dominioMensajes;

    private final ExecutorService cola = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "correo-comprobantes");
        t.setDaemon(true);
        return t;
    });

    public EntregaComprobanteService(EnvioCorreoRepository envios, IntentoCorreoRepository intentos,
                                     ComprobanteRepository comprobantes, NotificadorCorreo notificador,
                                     GeneradorPdfFacturacionService generadorPdf, EmpresaProperties empresa,
                                     PlatformTransactionManager txManager,
                                     @Value("${app.correo.max-intentos:5}") int maxIntentos,
                                     @Value("${app.correo.minutos-en-proceso:10}") long minutosEnProceso,
                                     @Value("${app.correo.conciliar-dias:3}") long diasConciliacion,
                                     @Value("${spring.mail.username:}") String remitente) {
        this.envios = envios;
        this.intentos = intentos;
        this.comprobantes = comprobantes;
        this.notificador = notificador;
        this.generadorPdf = generadorPdf;
        this.empresa = empresa;
        this.tx = new TransactionTemplate(txManager);
        this.maxIntentos = maxIntentos;
        this.minutosEnProceso = minutosEnProceso;
        this.diasConciliacion = diasConciliacion;
        int arroba = remitente == null ? -1 : remitente.indexOf('@');
        this.dominioMensajes = arroba > 0 ? remitente.substring(arroba + 1) : "sgejd.local";
    }

    @PreDestroy
    void detener() {
        cola.shutdown();
        try {
            cola.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ── Reglas ──────────────────────────────────────────────────────────────

    /**
     * Al cliente solo se le envía un comprobante con validez ante SUNAT (revisión externa F-01). Uno PENDIENTE o
     * RECHAZADO no la tiene; uno del modo simulado tampoco, aunque figure ACEPTADO, porque nunca salió a SUNAT; ni uno
     * aceptado por la beta de SUNAT (D-12). Lo decide el ambiente que quedó REGISTRADO EN EL COMPROBANTE al aceptarlo,
     * no la configuración actual: si el servidor pasa de beta a producción, lo aceptado por la beta sigue sin validez.
     *
     * @return el motivo para no enviarlo, o null si se puede enviar
     */
    public static String motivoParaNoEnviar(Comprobante c) {
        String numero = c.getSerie() + "-" + c.getNumero();
        if (c.getEstadoSunat() == null || !c.getEstadoSunat().valido()) {
            return numero + " todavía no tiene validez ante SUNAT (estado " + c.getEstadoSunat()
                    + "): se podrá enviar al cliente cuando SUNAT lo acepte.";
        }
        if (c.isReemplazado()) {
            return numero + " fue reemplazado por otro comprobante: no se envía al cliente.";
        }
        Comprobante.AmbienteEmision ambiente = c.getAmbienteEmision();
        if (ambiente == Comprobante.AmbienteEmision.SIMULADO || c.getEnvio().getXmlFirmado() == null) {
            return numero + " se emitió en modo simulado (no se envió a SUNAT): no se envía al cliente como comprobante.";
        }
        if (ambiente == Comprobante.AmbienteEmision.BETA) {
            return numero + " fue aceptado por la beta de SUNAT (pruebas): no tiene validez tributaria y no se envía al cliente.";
        }
        if (ambiente == null || ambiente == Comprobante.AmbienteEmision.NO_REGISTRADO) {
            return numero + " se aceptó antes de que SGE-JD registrara el ambiente de SUNAT (producción o pruebas): no se"
                    + " envía como comprobante tributario. Verifique su validez en SUNAT (Consulta de validez del CPE).";
        }
        return null;
    }

    /** Por qué esa dirección no sirve para enviar, o null si es válida. */
    public static String problemaConDestinatario(String correo) {
        if (correo == null || correo.isBlank()) {
            return "El cliente no tiene correo registrado. Agréguelo en Clientes y vuelva a enviar.";
        }
        String c = correo.trim();
        if (c.length() > 200 || !CORREO.matcher(c).matches()) {
            return "El correo del cliente (" + c + ") no es una dirección válida. Corríjalo en Clientes y vuelva a enviar.";
        }
        try {
            new jakarta.mail.internet.InternetAddress(c, true).validate();
        } catch (jakarta.mail.internet.AddressException e) {
            return "El correo del cliente (" + c + ") no es una dirección válida. Corríjalo en Clientes y vuelva a enviar.";
        }
        return null;
    }

    /** Espera antes del intento siguiente (null = ya no se reintenta solo). */
    static Duration esperaTras(int intentosHechos, int maxIntentos) {
        if (intentosHechos >= maxIntentos) return null;
        return ESPERAS[Math.min(Math.max(intentosHechos, 1), ESPERAS.length) - 1];
    }

    private static String correoDelCliente(Comprobante c) {
        String correo = c.getVenta() == null || c.getVenta().getCliente() == null ? null : c.getVenta().getCliente().getCorreo();
        return correo == null || correo.isBlank() ? null : correo.trim();
    }

    // ── Solicitudes ─────────────────────────────────────────────────────────

    /**
     * SUNAT aceptó el comprobante: deja su envío automático PENDIENTE y lo pone en la cola. Si no corresponde enviarlo
     * (sin validez, beta, cliente sin correo) o ya tiene su envío automático (el evento llegó dos veces), no hace nada.
     *
     * @return el id del envío creado
     */
    public Optional<Long> solicitarAutomatico(Long comprobanteId) {
        Long id;
        try {
            id = tx.execute(s -> {
                // Bloqueado: el reenvío manual y la conciliación deciden en orden, nunca a la vez
                Comprobante c = comprobantes.bloquear(comprobanteId).orElse(null);
                if (c == null) return null;
                String motivo = motivoParaNoEnviar(c);
                if (motivo != null) {
                    log.info("Comprobante {}: no se envía por correo. {}", comprobanteId, motivo);
                    return null;
                }
                String correo = correoDelCliente(c);
                if (correo == null) {
                    log.info("Comprobante {} aceptado; no se envía por correo: el cliente no tiene correo", comprobanteId);
                    return null;
                }
                if (envios.existsByComprobanteId(comprobanteId)) {
                    // Ya tiene su envío automático, o se envió a mano (p. ej. el evento llegó tarde): no se duplica
                    log.info("Comprobante {}: ya tiene un envío por correo; no se crea el automático", comprobanteId);
                    return null;
                }
                return envios.saveAndFlush(EnvioCorreo.nuevo(comprobanteId, correo, EnvioCorreo.Tipo.AUTOMATICO, null)).getId();
            });
        } catch (DataIntegrityViolationException e) {
            // Otro hilo lo creó en el mismo instante: el índice único UX_ENVIO_CORREO_AUTOMATICO lo impidió
            log.info("Comprobante {}: su envío automático por correo ya existía", comprobanteId);
            return Optional.empty();
        }
        if (id != null) encolar(id);
        return Optional.ofNullable(id);
    }

    /**
     * Reenvío manual desde Ventas, siempre al correo registrado del cliente (nunca a una dirección escrita a mano). Si
     * ya se entregó al servidor de correo (o quedó INCIERTO), hay que confirmarlo: así nadie lo reenvía sin querer.
     */
    public EntregaCorreoDTO solicitarReenvio(Long comprobanteId, boolean confirmar, String usuario) {
        EnvioCorreo creado = tx.execute(s -> {
            Comprobante c = comprobantes.bloquear(comprobanteId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comprobante no encontrado: " + comprobanteId));
            String motivo = motivoParaNoEnviar(c);
            if (motivo != null) throw new ResponseStatusException(HttpStatus.CONFLICT, motivo);
            String correo = correoDelCliente(c);
            String problema = problemaConDestinatario(correo);
            if (problema != null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, problema);
            if (envios.existsByComprobanteIdAndEstadoIn(comprobanteId, List.of(Estado.PENDIENTE, Estado.EN_PROCESO))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Ya hay un envío por correo de este comprobante en curso. Espere a que termine.");
            }
            if (!confirmar && envios.existsByComprobanteIdAndEstadoIn(comprobanteId, List.of(Estado.ENVIADO, Estado.INCIERTO))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Este comprobante ya se envió por correo. Confirme si quiere enviarlo otra vez.");
            }
            EnvioCorreo manual = envios.save(EnvioCorreo.nuevo(comprobanteId, correo, EnvioCorreo.Tipo.MANUAL, usuario));
            // Los FALLIDOS que iban a reintentarse solos ya no: este reenvío los reemplaza (si no, saldría dos veces).
            // Es la misma transacción y el comprobante sigue bloqueado: ningún trabajador los puede tomar a la vez.
            for (EnvioCorreo programado : envios.findByComprobanteIdAndEstadoAndProximoIntentoIsNotNull(comprobanteId, Estado.FALLIDO)) {
                programado.setProximoIntento(null);
                programado.setUltimoError(Objects.toString(programado.getUltimoError(), "") + " Reintento cancelado: " + (usuario == null ? "se" : usuario)
                        + " pidió un reenvío manual (envío #" + manual.getId() + ").");
                envios.save(programado);
            }
            return manual;
        });
        log.info("Reenvío por correo del comprobante {} pedido por {}", comprobanteId, usuario);
        encolar(creado.getId());
        return entrega(comprobanteId);
    }

    /** A la cola de correos al confirmarse la transacción actual (o ya, si no hay una): el trabajador debe ver la fila. */
    private void encolar(Long envioId) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            enviarALaCola(envioId);
                        }
                    });
        } else {
            enviarALaCola(envioId);
        }
    }

    private void enviarALaCola(Long envioId) {
        cola.submit(() -> {
            try {
                procesar(envioId);
            } catch (Exception e) {
                log.error("Error inesperado al enviar por correo (envío {}): {}", envioId, e.getMessage(), e);
            }
        });
    }

    // ── Envío ───────────────────────────────────────────────────────────────

    /** Lo que se va a enviar, o por qué no se puede (definitivo = reintentar no lo arregla). */
    private record Preparado(MensajeCorreo mensaje, String adjuntos, String error, boolean definitivo) {
        static Preparado error(String error, boolean definitivo) {
            return new Preparado(null, null, error, definitivo);
        }
    }

    /**
     * Toma el envío, arma el correo y lo entrega al servidor SMTP. Devuelve el estado final, o null si no lo tomó
     * (otro trabajador lo tiene, ya se envió o todavía no le toca).
     */
    public Estado procesar(Long envioId) {
        LocalDateTime inicio = LocalDateTime.now();
        String messageId = "<" + UUID.randomUUID() + ".sgejd-" + envioId + "@" + dominioMensajes + ">";

        // 1. Tomarlo (transacción corta): bloquea el comprobante para que nadie más envíe el mismo a la vez
        Boolean tomado = tx.execute(s -> {
            EnvioCorreo e = envios.findById(envioId).orElse(null);
            if (e == null) return false;
            comprobantes.bloquear(e.getComprobanteId());
            if (envios.existsByComprobanteIdAndEstadoIn(e.getComprobanteId(), List.of(Estado.EN_PROCESO))) return false;
            if (e.getTipo() == EnvioCorreo.Tipo.AUTOMATICO && e.getEstado() == Estado.FALLIDO
                    && envios.existsByComprobanteIdAndEstadoIn(e.getComprobanteId(), List.of(Estado.ENVIADO, Estado.INCIERTO))) {
                // Otro envío del mismo comprobante ya se entregó (o quedó por confirmar): reintentar lo duplicaría
                e.setProximoIntento(null);
                e.setUltimoError((Objects.toString(e.getUltimoError(), "") + " No se reintenta: el comprobante ya se entregó en otro envío.").trim());
                envios.save(e);
                return false;
            }
            return envios.tomar(envioId, inicio, messageId) == 1;
        });
        if (!Boolean.TRUE.equals(tomado)) return null;

        // 2. Prepararlo (lectura): reglas, destinatario, XML exacto de Oracle y PDF del mismo comprobante
        Preparado p;
        try {
            p = tx.execute(s -> preparar(envioId, messageId));
        } catch (Exception ex) {
            p = Preparado.error("No se pudo preparar el correo: " + ex.getMessage(), false);
        }

        // 3. Entregarlo al servidor SMTP, sin transacción abierta
        String error = p.error();
        boolean definitivo = p.definitivo();
        String aceptadoComo = null;
        if (error == null) {
            try {
                aceptadoComo = notificador.enviarConAdjuntos(p.mensaje());
            } catch (Exception ex) {
                error = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
                definitivo = false;
            }
        }

        // 4. Registrar el resultado (transacción corta)
        final String errorFinal = error;
        final boolean definitivoFinal = definitivo;
        final String idAceptado = aceptadoComo;
        final String adjuntos = p.adjuntos();
        return tx.execute(s -> {
            EnvioCorreo e = envios.findById(envioId).orElseThrow();
            e.setEnProcesoDesde(null);
            if (errorFinal == null) {
                e.setEstado(Estado.ENVIADO);
                e.setFechaAceptacionSmtp(LocalDateTime.now());
                if (idAceptado != null) e.setMessageId(idAceptado);
                e.setUltimoError(null);
                e.setProximoIntento(null);
                intentos.save(IntentoCorreo.de(e, inicio, IntentoCorreo.Resultado.ENVIADO,
                        "Aceptado por el servidor de correo para " + e.getDestinatario() + ".", adjuntos));
            } else {
                Duration espera = definitivoFinal ? null : esperaTras(e.getIntentos(), maxIntentos);
                e.setEstado(Estado.FALLIDO);
                e.setProximoIntento(espera == null ? null : LocalDateTime.now().plus(espera));
                String detalle = errorFinal + (espera != null
                        ? " Se vuelve a intentar solo el " + e.getProximoIntento().format(HORA) + "."
                        : definitivoFinal ? " No se reintenta solo: corrija el dato y use \"Reenviar por correo\"."
                        : " Se agotaron los intentos automáticos: puede usar \"Reenviar por correo\".");
                e.setUltimoError(detalle);
                intentos.save(IntentoCorreo.de(e, inicio, IntentoCorreo.Resultado.FALLIDO, detalle, adjuntos));
                log.warn("Correo del comprobante {} (envío {}, intento {}) no enviado: {}", e.getComprobanteId(), envioId,
                        e.getIntentos(), errorFinal);
            }
            envios.save(e);
            return e.getEstado();
        });
    }

    private Preparado preparar(Long envioId, String messageId) {
        EnvioCorreo e = envios.findById(envioId).orElseThrow();
        Comprobante c = comprobantes.findById(e.getComprobanteId()).orElse(null);
        if (c == null) return Preparado.error("El comprobante ya no existe.", true);
        String motivo = motivoParaNoEnviar(c);
        if (motivo != null) return Preparado.error(motivo, true);
        String problema = problemaConDestinatario(e.getDestinatario());
        if (problema != null) return Preparado.error(problema, true);
        byte[] xml = c.getEnvio().getXmlFirmado();
        String problemaXml = AdjuntosComprobante.problemaConXml(xml, empresa.ruc(), c);
        if (problemaXml != null) return Preparado.error(problemaXml, true);
        byte[] pdf;
        try {
            pdf = generadorPdf.generarFacturaPdfBytes(c);
        } catch (Exception ex) {
            // No se manda un correo incompleto: queda FALLIDO y se reintenta
            return Preparado.error("No se pudo generar el PDF del comprobante: " + ex.getMessage(), false);
        }
        if (pdf == null || pdf.length == 0) return Preparado.error("El PDF del comprobante salió vacío.", false);
        List<Adjunto> adjuntos = AdjuntosComprobante.armar(empresa.ruc(), c, pdf, xml);
        var datos = new PlantillaCorreoComprobante.Datos(c.getTipo().name(), c.getSerie() + "-" + c.getNumero(),
                c.getFechaEmision(), c.getVenta().getMoneda() == null ? "SOLES" : c.getVenta().getMoneda().name(), c.getTotal(),
                c.getEstadoSunat() == Comprobante.EstadoSunat.OBSERVADO,
                c.getReceptorNombre() != null ? c.getReceptorNombre() : c.getVenta().getCliente().getNombreORazonSocial(),
                adjuntos.stream().map(Adjunto::nombre).toList());
        MensajeCorreo m = new MensajeCorreo(e.getDestinatario(), PlantillaCorreoComprobante.asunto(datos, empresa),
                PlantillaCorreoComprobante.html(datos, empresa), adjuntos, messageId);
        return new Preparado(m, AdjuntosComprobante.resumen(adjuntos), null, false);
    }

    // ── Revisión periódica y recuperación ───────────────────────────────────

    /**
     * Cada minuto (y al arrancar): marca como INCIERTOS los envíos interrumpidos y entrega los pendientes y los
     * fallidos a los que ya les toca. Así un reinicio del backend no deja correos olvidados.
     */
    @Scheduled(fixedDelayString = "${app.correo.revision-ms:60000}", initialDelayString = "${app.correo.revision-inicial-ms:30000}")
    public void revisar() {
        recuperarInterrumpidos();
        conciliarAceptadosSinEnvio();
        List<Long> ids = envios.porProcesar(LocalDateTime.now(), Limit.of(20));
        for (Long id : ids) {
            try {
                procesar(id);
            } catch (Exception e) {
                log.error("Error al reintentar el correo del envío {}: {}", id, e.getMessage());
            }
        }
    }

    /**
     * Conciliación: comprobantes que SUNAT aceptó en PRODUCCION en los últimos app.correo.conciliar-dias días, de un
     * cliente con correo, y que no tienen su envío automático (p. ej. el backend se cayó justo después de la respuesta de
     * SUNAT y el evento se perdió). Les crea el envío; las mismas reglas de siempre lo vuelven a comprobar todo. Deja
     * 2 minutos de margen al camino normal, y el índice único impide duplicar si los dos llegan a la vez.
     *
     * @return cuántos envíos creó
     */
    public int conciliarAceptadosSinEnvio() {
        LocalDateTime ahora = LocalDateTime.now();
        List<Long> sinEnvio = envios.aceptadosSinEnvioAutomatico(ahora.minusDays(diasConciliacion), ahora.minusMinutes(2),
                Limit.of(20));
        int creados = 0;
        for (Long id : sinEnvio) {
            if (solicitarAutomatico(id).isPresent()) {
                creados++;
                log.warn("Conciliación: el comprobante {} estaba aceptado en producción sin envío por correo; se creó", id);
            }
        }
        return creados;
    }

    /** EN_PROCESO por más de app.correo.minutos-en-proceso: se interrumpió y no se sabe si salió. Quedan INCIERTOS. */
    public int recuperarInterrumpidos() {
        Integer n = tx.execute(s -> {
            List<EnvioCorreo> trabados = envios.findByEstadoAndEnProcesoDesdeBefore(Estado.EN_PROCESO,
                    LocalDateTime.now().minusMinutes(minutosEnProceso));
            for (EnvioCorreo e : trabados) {
                LocalDateTime desde = e.getEnProcesoDesde();
                String mensaje = "El envío se interrumpió (intento " + e.getIntentos() + ", Message-ID " + e.getMessageId()
                        + ") y no se sabe si el servidor de correo llegó a aceptarlo. No se reenvía solo para no mandarlo dos"
                        + " veces: confirme con el cliente y, si no le llegó, use \"Reenviar por correo\".";
                e.setEstado(Estado.INCIERTO);
                e.setEnProcesoDesde(null);
                e.setProximoIntento(null);
                e.setUltimoError(mensaje);
                envios.save(e);
                intentos.save(IntentoCorreo.de(e, desde != null ? desde : LocalDateTime.now(), IntentoCorreo.Resultado.INCIERTO, mensaje, null));
                log.warn("Correo del comprobante {} (envío {}) quedó INCIERTO: se interrumpió en pleno envío", e.getComprobanteId(), e.getId());
            }
            return trabados.size();
        });
        return n == null ? 0 : n;
    }

    // ── Consultas ───────────────────────────────────────────────────────────

    /** Detalle de la entrega por correo de un comprobante, con todos sus envíos e intentos. */
    public EntregaCorreoDTO entrega(Long comprobanteId) {
        return tx.execute(s -> {
            Comprobante c = comprobantes.findById(comprobanteId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comprobante no encontrado: " + comprobanteId));
            List<EnvioCorreo> lista = envios.findByComprobanteIdOrderByIdDesc(comprobanteId);
            Map<Long, List<IntentoCorreo>> porEnvio = intentosDe(lista);
            List<EntregaCorreoDTO.Envio> historial = lista.stream()
                    .map(e -> EntregaCorreoDTO.Envio.de(e, porEnvio.getOrDefault(e.getId(), List.of()))).toList();
            return armar(c, lista, historial.isEmpty() ? null : historial.get(0), historial);
        });
    }

    /** Resumen (sin historial) de varios comprobantes, para la lista de Ventas: una sola consulta. */
    public Map<Long, EntregaCorreoDTO> resumenes(List<Comprobante> lista) {
        if (lista.isEmpty()) return Map.of();
        Map<Long, List<EnvioCorreo>> porComprobante = envios.findByComprobanteIdInOrderByIdDesc(
                        lista.stream().map(Comprobante::getId).toList()).stream()
                .collect(Collectors.groupingBy(EnvioCorreo::getComprobanteId));
        Map<Long, EntregaCorreoDTO> r = new HashMap<>();
        for (Comprobante c : lista) {
            List<EnvioCorreo> suyos = porComprobante.getOrDefault(c.getId(), List.of());
            EntregaCorreoDTO.Envio ultimo = suyos.isEmpty() ? null : EntregaCorreoDTO.Envio.de(suyos.get(0), List.of());
            r.put(c.getId(), armar(c, suyos, ultimo, List.of()));
        }
        return r;
    }

    private EntregaCorreoDTO armar(Comprobante c, List<EnvioCorreo> lista, EntregaCorreoDTO.Envio ultimo,
                                   List<EntregaCorreoDTO.Envio> historial) {
        String correo = correoDelCliente(c);
        String motivo = motivoParaNoEnviar(c);
        if (motivo == null && correo == null) motivo = "El cliente no tiene correo registrado: agréguelo en Clientes para poder enviárselo.";
        LocalDateTime ultimaAceptacion = lista.stream().map(EnvioCorreo::getFechaAceptacionSmtp).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
        return new EntregaCorreoDTO(c.getId(), c.getAmbienteEmision() == null ? null : c.getAmbienteEmision().name(),
                correo, motivo, ultimo, lista.size(), ultimaAceptacion, historial);
    }

    private Map<Long, List<IntentoCorreo>> intentosDe(List<EnvioCorreo> lista) {
        if (lista.isEmpty()) return Map.of();
        return intentos.findByEnvioCorreoIdInOrderByIdDesc(lista.stream().map(EnvioCorreo::getId).toList()).stream()
                .collect(Collectors.groupingBy(IntentoCorreo::getEnvioCorreoId));
    }
}
