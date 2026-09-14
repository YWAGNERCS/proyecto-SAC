package com.jdrefrigeracion.facturacion.integrations;
import com.jdrefrigeracion.facturacion.models.Comprobante;

import org.springframework.stereotype.Component;

/**
 * Implementación simulada de Nubefact.
 * En un escenario real, aquí se usaría RestTemplate para hacer un POST
 * a https://api.nubefact.com/api/v1/ con el JSON requerido.
 */
@Component
public class NubefactClientSimulado implements OseClient {

    @Override
    public RespuestaOse emitir(Comprobante comprobante) {
        System.out.println("Enviando JSON a Nubefact para el comprobante: " + comprobante.getSerie() + "-" + comprobante.getNumero());
        
        // Simulamos una respuesta exitosa del OSE
        String urlBase = "https://www.nubefact.com/api/v1/demo/pdf/";
        String pdf = urlBase + comprobante.getSerie() + "-" + comprobante.getNumero() + ".pdf";
        String xml = urlBase + comprobante.getSerie() + "-" + comprobante.getNumero() + ".xml";

        return new RespuestaOse(true, pdf, xml, null);
    }
}
