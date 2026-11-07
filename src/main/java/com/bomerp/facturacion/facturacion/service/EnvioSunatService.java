package com.bomerp.facturacion.facturacion.service;

import com.bomerp.facturacion.comprobante.entity.Comprobante;
import com.bomerp.facturacion.comprobante.entity.Comprobante.EstadoSunat;
import com.bomerp.facturacion.comprobante.entity.EnvioSunat;
import com.bomerp.facturacion.comprobante.repository.ComprobanteRepository;
import com.bomerp.facturacion.notacredito.entity.NotaCredito;
import com.bomerp.facturacion.notacredito.repository.NotaCreditoRepository;
import com.bomerp.facturacion.ose.integration.OseClient;
import com.bomerp.facturacion.ose.integration.OseClient.RespuestaOse;
import com.bomerp.ventas.venta.entity.Venta;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Envía comprobantes y notas de crédito a SUNAT fuera de la transacción de la venta.
 *
 * - Se programan al confirmarse la transacción que los creó: si SUNAT tarda o está caída, la
 *   venta ya quedó guardada y el usuario no espera.
 * - Una sola cola, de uno en uno y con una pausa entre envíos: SUNAT corta los envíos en ráfaga
 *   (la beta responde HTTP 401 al tercer envío seguido en menos de un segundo).
 * - Lo que queda PENDIENTE (SUNAT no respondió) se reintenta cada pocos minutos, hasta un máximo.
 * - Una nota de crédito sale recién cuando su comprobante original fue aceptado.
 */
@Service
@Slf4j
public class EnvioSunatService {

    /** Después de tantos intentos se deja de reintentar solo: el mensaje queda a la vista para revisarlo. */
    static final int MAX_INTENTOS = 20;

    /** Código 1033 de SUNAT: el número ya estaba registrado por un envío anterior (ver SunatDirectoClient). */
    static final int CODIGO_POR_CONFIRMAR = 1033;

    /** Cuántos pendientes toma cada vuelta del reintento automático. */
    static final org.springframework.data.domain.Limit LOTE = org.springframework.data.domain.Limit.of(20);

    private final ComprobanteRepository comprobantes;
    private final NotaCreditoRepository notas;
    private final OseClient ose;
    private final ApplicationEventPublisher eventos;
    private final TransactionTemplate tx;
    private final long pausaMs;
    private final ExecutorService cola = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "envio-sunat");
        t.setDaemon(true);
        return t;
    });

    public EnvioSunatService(ComprobanteRepository comprobantes, NotaCreditoRepository notas, OseClient ose,
                             ApplicationEventPublisher eventos, PlatformTransactionManager txManager,
                             @Value("${app.facturacion.pausa-entre-envios-ms:1500}") long pausaMs) {
        this.comprobantes = comprobantes;
        this.notas = notas;
        this.ose = ose;
        this.eventos = eventos;
        this.tx = new TransactionTemplate(txManager);
        this.pausaMs = pausaMs;
    }

    /** Comprobante aceptado por SUNAT: Facturación lo envía por correo al cliente (si tiene). */
    public record ComprobanteAceptadoEvent(Long comprobanteId) {}

    public void programarComprobante(Long id) {
        programar(() -> enviarComprobante(id));
    }

    public void programarNota(Long id) {
        programar(() -> enviarNota(id));
    }

    /** Al confirmarse la transacción actual (o ya, si no hay una), a la cola de envíos. */
    private void programar(Runnable envio) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    encolar(envio);
                }
            });
        } else {
            encolar(envio);
        }
    }

    private void encolar(Runnable envio) {
        cola.submit(() -> {
            try {
                envio.run();
            } catch (Exception e) {
                log.error("Error inesperado al enviar a SUNAT: {}", e.getMessage(), e);
            }
            pausa();
        });
    }

    /** Envía un comprobante PENDIENTE y guarda la respuesta de SUNAT. Devuelve el estado final. */
    public EstadoSunat enviarComprobante(Long id) {
        EstadoSunat estado = tx.execute(s -> {
            Comprobante c = comprobantes.findById(id).orElse(null);
            if (c == null || c.getEstadoSunat() != EstadoSunat.PENDIENTE) {
                return c == null ? null : c.getEstadoSunat();
            }
            if (!tieneNotaSiSuVentaFueAnulada(c)) {
                c.getEnvio().registrarIntento(null, "No se envía: la venta #" + c.getVenta().getId()
                        + " está anulada y no se pudo crear su nota de crédito. Revise el registro del backend.", null, null);
                return EstadoSunat.PENDIENTE;
            }
            RespuestaOse r = ose.emitir(c);
            c.setEstadoSunat(aplicar(c.getEnvio(), r));
            if (r.enlacePdf() != null) c.setEnlacePdf(r.enlacePdf());
            if (r.enlaceXml() != null) c.setEnlaceXml(r.enlaceXml());
            return c.getEstadoSunat();
        });
        if (estado != null && estado.valido()) {
            eventos.publishEvent(new ComprobanteAceptadoEvent(id));
        }
        return estado;
    }

    /**
     * Una factura o boleta de una venta anulada solo puede salir junto con su nota de crédito: si SUNAT
     * la aceptara sola, quedaría vigente una venta que ya no existe (auditoría M10, venta #285: se anuló
     * cuando la nota todavía no se creaba para comprobantes pendientes). Si falta la nota, se pide a
     * Facturación que la cree ahora, en esta misma transacción, y sale después de la factura.
     *
     * @return false si la venta está anulada y aun así no tiene nota: entonces no se envía
     */
    private boolean tieneNotaSiSuVentaFueAnulada(Comprobante c) {
        if (c.getVenta() == null || c.getVenta().getEstado() != Venta.EstadoVenta.ANULADA
                || notas.findByComprobanteReferencia_Id(c.getId()).isPresent()) {
            return true;
        }
        log.warn("{}-{} es de la venta anulada #{} y no tiene nota de crédito: se crea antes de enviarlo",
                c.getSerie(), c.getNumero(), c.getVenta().getId());
        eventos.publishEvent(new VentaAnuladaSinNotaEvent(c.getVenta().getId()));
        boolean creada = notas.findByComprobanteReferencia_Id(c.getId()).isPresent();
        if (!creada) {
            log.error("{}-{} no se envía: la venta #{} está anulada y no se pudo crear su nota de crédito",
                    c.getSerie(), c.getNumero(), c.getVenta().getId());
        }
        return creada;
    }

    /** Comprobante de una venta anulada que se iba a enviar sin su nota de crédito: Facturación la crea. */
    public record VentaAnuladaSinNotaEvent(Long ventaId) {}

    /** Envía una nota de crédito PENDIENTE, solo si su comprobante original ya tiene validez. */
    public EstadoSunat enviarNota(Long id) {
        return tx.execute(s -> {
            NotaCredito n = notas.findById(id).orElse(null);
            if (n == null || n.getEstadoSunat() != EstadoSunat.PENDIENTE) {
                return n == null ? null : n.getEstadoSunat();
            }
            EstadoSunat original = n.getComprobanteReferencia().getEstadoSunat();
            if (original == EstadoSunat.PENDIENTE) {
                return EstadoSunat.PENDIENTE;   // espera al comprobante; el reintento la vuelve a tomar
            }
            if (original == EstadoSunat.RECHAZADO) {
                // Un comprobante sin validez no necesita anularse ante SUNAT
                n.getEnvio().registrarIntento(null, "No se envía: el comprobante original fue rechazado por SUNAT.", null, null);
                n.setEstadoSunat(EstadoSunat.RECHAZADO);
                return n.getEstadoSunat();
            }
            RespuestaOse r = ose.emitirNotaCredito(n);
            n.setEstadoSunat(aplicar(n.getEnvio(), r));
            if (r.enlacePdf() != null) n.setEnlacePdf(r.enlacePdf());
            if (r.enlaceXml() != null) n.setEnlaceXml(r.enlaceXml());
            return n.getEstadoSunat();
        });
    }

    /**
     * Reenvío manual (botón "Reenviar"): un comprobante RECHAZADO (después de corregir el dato, p. ej. el
     * RUC del cliente; SUNAT permite reenviar el mismo número) o PENDIENTE que agotó los reintentos.
     * Vuelve a PENDIENTE con los intentos en cero y entra a la cola. 409 si ya tiene validez.
     */
    public void reenviarComprobante(Long id) {
        tx.executeWithoutResult(s -> {
            Comprobante c = comprobantes.findById(id).orElseThrow(() -> noEncontrado("Comprobante", id));
            reabrir(c.getEstadoSunat(), c.getEnvio(), c.getSerie() + "-" + Integer.parseInt(c.getNumero()));
            c.setEstadoSunat(EstadoSunat.PENDIENTE);
        });
        encolar(() -> enviarComprobante(id));
    }

    public void reenviarNota(Long id) {
        tx.executeWithoutResult(s -> {
            NotaCredito n = notas.findById(id).orElseThrow(() -> noEncontrado("Nota de crédito", id));
            reabrir(n.getEstadoSunat(), n.getEnvio(), n.getSerie() + "-" + Integer.parseInt(n.getNumero()));
            n.setEstadoSunat(EstadoSunat.PENDIENTE);
        });
        encolar(() -> enviarNota(id));
    }

    private static void reabrir(EstadoSunat estado, EnvioSunat envio, String numero) {
        if (estado.valido()) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                    numero + " ya fue aceptado por SUNAT: no se vuelve a enviar.");
        }
        envio.setIntentos(0);
    }

    private static org.springframework.web.server.ResponseStatusException noEncontrado(String que, Long id) {
        return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                que + " no encontrado: " + id);
    }

    /** Pasa la respuesta al registro del envío y devuelve el nuevo estado. */
    private static EstadoSunat aplicar(EnvioSunat envio, RespuestaOse r) {
        envio.registrarIntento(r.codigo(), r.mensaje(), r.xmlFirmado(), r.cdr());
        return switch (r.resultado()) {
            case ACEPTADO -> EstadoSunat.ACEPTADO;
            case OBSERVADO -> EstadoSunat.OBSERVADO;
            case RECHAZADO -> EstadoSunat.RECHAZADO;
            case REINTENTAR -> EstadoSunat.PENDIENTE;
            // Sin validez (no se envía por correo ni cuenta como emitido) y fuera del reintento automático:
            // queda a la vista con el aviso para verificarlo en SUNAT.
            case POR_CONFIRMAR -> EstadoSunat.PENDIENTE;
        };
    }

    /**
     * Reintento automático: comprobantes y notas que siguen PENDIENTES (SUNAT no respondió o
     * nunca se llegaron a enviar, p. ej. si el backend se apagó justo después de la venta).
     */
    @Scheduled(initialDelayString = "${app.facturacion.reintento-inicial-ms:60000}",
            fixedDelayString = "${app.facturacion.reintento-ms:180000}")
    public void reintentarPendientes() {
        // El límite de intentos va en la consulta, antes del Top 20 (revisión externa F-02): si se filtrara
        // después, 20 comprobantes agotados al principio de la lista taparían para siempre a los siguientes.
        // Los que SUNAT respondió con 1033 (ya registrado, por confirmar) también quedan fuera: reenviarlos solo
        // repetiría el 1033 cada pocos minutos. Se ven en Ventas con el aviso para verificarlos en SUNAT.
        var pendientes = comprobantes.pendientesParaReintentar(EstadoSunat.PENDIENTE, MAX_INTENTOS, CODIGO_POR_CONFIRMAR, LOTE)
                .stream().map(Comprobante::getId).toList();
        var notasPendientes = notas.pendientesParaReintentar(EstadoSunat.PENDIENTE, MAX_INTENTOS, CODIGO_POR_CONFIRMAR, LOTE)
                .stream().map(NotaCredito::getId).toList();
        if (pendientes.isEmpty() && notasPendientes.isEmpty()) return;
        log.info("Reintento de envíos a SUNAT: {} comprobante(s) y {} nota(s) de crédito pendientes",
                pendientes.size(), notasPendientes.size());
        pendientes.forEach(id -> encolar(() -> enviarComprobante(id)));
        notasPendientes.forEach(id -> encolar(() -> enviarNota(id)));
    }

    private void pausa() {
        if (pausaMs <= 0) return;
        try {
            Thread.sleep(pausaMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @PreDestroy
    void detener() throws InterruptedException {
        cola.shutdown();
        cola.awaitTermination(10, TimeUnit.SECONDS);
    }
}
