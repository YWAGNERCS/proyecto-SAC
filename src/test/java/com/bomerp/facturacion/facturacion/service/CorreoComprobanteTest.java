package com.bomerp.facturacion.facturacion.service;

import com.bomerp.facturacion.comprobante.entity.Comprobante;
import com.bomerp.facturacion.comprobante.entity.Comprobante.AmbienteEmision;
import com.bomerp.facturacion.comprobante.entity.Comprobante.EstadoSunat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Revisión externa F-01: al cliente solo se le envía por correo un comprobante con validez ante SUNAT, y nunca uno del
 * modo simulado ni uno aceptado por la beta de SUNAT (D-12). Lo decide el ambiente registrado en el comprobante.
 */
class CorreoComprobanteTest {

    private static Comprobante comprobante(EstadoSunat estado, AmbienteEmision ambiente, boolean conXmlFirmado) {
        Comprobante c = new Comprobante();
        c.setSerie("F001");
        c.setNumero("00000129");
        c.setEstadoSunat(estado);
        c.registrarAmbiente(ambiente);
        if (conXmlFirmado) c.getEnvio().setXmlFirmado(new byte[]{1});
        return c;
    }

    @Test
    void pendienteNoSeEnvia() {
        assertThat(FacturacionService.motivoParaNoEnviarAlCliente(comprobante(EstadoSunat.PENDIENTE, null, true)))
                .contains("F001-00000129").contains("todavía no tiene validez");
    }

    @Test
    void rechazadoNoSeEnvia() {
        assertThat(FacturacionService.motivoParaNoEnviarAlCliente(comprobante(EstadoSunat.RECHAZADO, AmbienteEmision.PRODUCCION, true)))
                .contains("todavía no tiene validez");
    }

    @Test
    void aceptadoEnModoSimuladoNoSeEnvia() {
        assertThat(FacturacionService.motivoParaNoEnviarAlCliente(comprobante(EstadoSunat.ACEPTADO, AmbienteEmision.SIMULADO, false)))
                .contains("modo simulado");
    }

    @Test
    void aceptadoYObservadoDeSunatDeProduccionSiSeEnvian() {
        assertThat(FacturacionService.motivoParaNoEnviarAlCliente(comprobante(EstadoSunat.ACEPTADO, AmbienteEmision.PRODUCCION, true))).isNull();
        assertThat(FacturacionService.motivoParaNoEnviarAlCliente(comprobante(EstadoSunat.OBSERVADO, AmbienteEmision.PRODUCCION, true))).isNull();
    }

    @Test
    void aceptadoPorLaBetaDeSunatNoSeEnvia() {
        assertThat(FacturacionService.motivoParaNoEnviarAlCliente(comprobante(EstadoSunat.ACEPTADO, AmbienteEmision.BETA, true)))
                .contains("beta de SUNAT").contains("no tiene validez");
    }
}
