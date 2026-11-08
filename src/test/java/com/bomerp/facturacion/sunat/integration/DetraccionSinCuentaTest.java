package com.bomerp.facturacion.sunat.integration;

import com.bomerp.config.empresa.EmpresaProperties;
import com.bomerp.ventas.venta.entity.Venta;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Sin la cuenta de detracciones de JD, una venta con detracción no puede emitirse todavía (no se conecta a SUNAT). */
class DetraccionSinCuentaTest {

    private static SunatDirectoClient cliente(String cuenta) {
        return new SunatDirectoClient(
                new SunatProperties("BETA", "https://e-beta.sunat.gob.pe/ol-ti-itcpfegem-beta/billService", "MODDATOS", "x", null, null),
                new EmpresaProperties("JD Refrigeración S.A.C.", "20000000001", "Lima", cuenta));
    }

    private static Venta venta(Venta.TipoDeduccion deduccion) {
        Venta v = new Venta();
        v.setTipoDeduccion(deduccion);
        return v;
    }

    @Test
    void conDetraccionYSinCuentaQuedaPorEmitir() {
        assertThat(cliente(null).motivoParaNoEmitir(venta(Venta.TipoDeduccion.DETRACCION))).contains("CUENTA_DETRACCIONES");
        assertThat(cliente("  ").motivoParaNoEmitir(venta(Venta.TipoDeduccion.DETRACCION))).isNotNull();
    }

    /** D-12: lo que acepta la beta de SUNAT no tiene validez tributaria; lo de producción sí. */
    @Test
    void soloLaSunatDeProduccionDaValidez() {
        assertThat(cliente(null).conValidezTributaria()).isFalse();
        SunatDirectoClient produccion = new SunatDirectoClient(
                new SunatProperties("PRODUCCION", "https://e-factura.sunat.gob.pe/ol-ti-itcpfegem/billService", "USUARIO1", "x", null, null),
                new EmpresaProperties("JD Refrigeración S.A.C.", "20000000001", "Lima", null));
        assertThat(produccion.conValidezTributaria()).isTrue();
    }

    @Test
    void conCuentaOSinDetraccionSeEmite() {
        assertThat(cliente("00-000-000000").motivoParaNoEmitir(venta(Venta.TipoDeduccion.DETRACCION))).isNull();
        assertThat(cliente(null).motivoParaNoEmitir(venta(Venta.TipoDeduccion.NINGUNA))).isNull();
        assertThat(cliente(null).motivoParaNoEmitir(venta(Venta.TipoDeduccion.RETENCION))).isNull();
    }
}
