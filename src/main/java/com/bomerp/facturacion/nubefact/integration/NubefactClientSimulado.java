package com.bomerp.facturacion.nubefact.integration;
import com.bomerp.facturacion.ose.integration.OseClient;
import com.bomerp.facturacion.ose.integration.OseClient.RespuestaOse;
import com.bomerp.facturacion.comprobante.entity.Comprobante;
import com.bomerp.facturacion.notacredito.entity.NotaCredito;

import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementación simulada de Nubefact.
 * En un escenario real, aquí se usaría RestTemplate para hacer un POST
 * a https://api.nubefact.com/api/v1/ con el JSON requerido.
 */
@Slf4j
@Component
public class NubefactClientSimulado implements OseClient {

    @Override
    public RespuestaOse emitir(Comprobante comprobante) {
        log.info("[SIMULADO - OSE] Comprobante {}-{} enviado a Nubefact", comprobante.getSerie(), comprobante.getNumero());
        
        // Simulamos una respuesta exitosa del OSE
        String urlBase = "https://www.nubefact.com/api/v1/demo/pdf/";
        String pdf = urlBase + comprobante.getSerie() + "-" + comprobante.getNumero() + ".pdf";
        String xml = urlBase + comprobante.getSerie() + "-" + comprobante.getNumero() + ".xml";

        return new RespuestaOse(true, pdf, xml, null);
    }

    @Override
    public RespuestaOse emitirNotaCredito(NotaCredito notaCredito) {
        String numero = notaCredito.getSerie() + "-" + notaCredito.getNumero();
        log.info("[SIMULADO - OSE] Nota de crédito {} (anula {}) enviada a Nubefact", numero, notaCredito.getNumeroComprobanteReferencia());

        String urlBase = "https://www.nubefact.com/api/v1/demo/pdf/";
        return new RespuestaOse(true, urlBase + numero + ".pdf", urlBase + numero + ".xml", null);
    }
}
