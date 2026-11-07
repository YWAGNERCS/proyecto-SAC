package com.bomerp.facturacion.facturacion.service;

import com.bomerp.facturacion.comprobante.entity.Comprobante;
import com.bomerp.facturacion.comprobante.entity.Comprobante.EstadoSunat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Revisión externa F-01: al cliente solo se le envía por correo ("Su Factura ... ha sido emitida") un
 * comprobante con validez ante SUNAT, y nunca uno del modo simulado.
 */
class CorreoComprobanteTest {

    private static Comprobante comprobante(EstadoSunat estado, boolean conXmlFirmado) {
        Comprobante c = new Comprobante();
        c.setSerie("F001");
        c.setNumero("00000129");
        c.setEstadoSunat(estado);
        if (conXmlFirmado) c.getEnvio().setXmlFirmado(new byte[]{1});
        return c;
    }

    @Test
    void pendienteNoSeEnvia() {
        assertThat(FacturacionService.motivoParaNoEnviarAlCliente(comprobante(EstadoSunat.PENDIENTE, true)))
                .contains("F001-00000129").contains("todavía no tiene validez");
    }

    @Test
    void rechazadoNoSeEnvia() {
        assertThat(FacturacionService.motivoParaNoEnviarAlCliente(comprobante(EstadoSunat.RECHAZADO, true)))
                .contains("todavía no tiene validez");
    }

    @Test
    void aceptadoEnModoSimuladoNoSeEnvia() {
        assertThat(FacturacionService.motivoParaNoEnviarAlCliente(comprobante(EstadoSunat.ACEPTADO, false)))
                .contains("modo simulado");
    }

    @Test
    void aceptadoYObservadoDeSunatSiSeEnvian() {
        assertThat(FacturacionService.motivoParaNoEnviarAlCliente(comprobante(EstadoSunat.ACEPTADO, true))).isNull();
        assertThat(FacturacionService.motivoParaNoEnviarAlCliente(comprobante(EstadoSunat.OBSERVADO, true))).isNull();
    }
}
