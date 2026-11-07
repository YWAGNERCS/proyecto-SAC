package com.bomerp.facturacion.sunat.integration;

import com.bomerp.config.empresa.EmpresaProperties;
import com.bomerp.facturacion.comprobante.entity.Comprobante;
import com.bomerp.facturacion.notacredito.entity.NotaCredito;
import com.bomerp.facturacion.ose.integration.OseClient;
import com.bomerp.facturacion.ose.integration.OseClient.Resultado;
import com.bomerp.ventas.venta.entity.Venta;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Envío directo a SUNAT con el usuario SOL y el certificado digital de JD (SEE - Del Contribuyente).
 * Se activa con app.facturacion.modo=sunat; la URL decide si es el ambiente beta (pruebas, sin
 * efecto tributario) o producción. Sin esa propiedad se usa el envío simulado.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.facturacion.modo", havingValue = "sunat")
public class SunatDirectoClient implements OseClient {

    private final XmlComprobante xml;
    private final SunatSoapClient sunat;
    private final SunatProperties props;
    private final EmpresaProperties empresa;
    private CertificadoSunat certificado;

    public SunatDirectoClient(SunatProperties props, EmpresaProperties empresa) {
        this.props = props;
        this.empresa = empresa;
        this.xml = new XmlComprobante(empresa);
        // SUNAT pide el usuario como RUC + usuario SOL (en beta: RUC + MODDATOS)
        this.sunat = new SunatSoapClient(props.url(), empresa.ruc() + props.usuario(), props.clave());
        log.info("Facturación: envío directo a SUNAT ({})", props.url());
    }

    @Override
    public RespuestaOse emitir(Comprobante comprobante) {
        // Una factura con detracción sin la cuenta del Banco de la Nación la rechaza SUNAT (3034). Es un
        // dato que le falta a JD, no al comprobante: no se envía y queda PENDIENTE; al configurar la
        // cuenta, el reintento automático la envía.
        if (comprobante.getVenta().getTipoDeduccion() == Venta.TipoDeduccion.DETRACCION
                && (empresa.cuentaDetracciones() == null || empresa.cuentaDetracciones().isBlank())) {
            return RespuestaOse.reintentar("No se envió: falta configurar la cuenta de detracciones de JD en el "
                    + "Banco de la Nación (CUENTA_DETRACCIONES). Se enviará sola cuando esté configurada.", null);
        }
        String nombre = xml.nombreArchivo(XmlComprobante.tipoDocumento(comprobante), comprobante.getSerie(), comprobante.getNumero());
        return enviar(nombre, () -> xml.factura(comprobante, certificado()));
    }

    @Override
    public RespuestaOse emitirNotaCredito(NotaCredito nota) {
        String nombre = xml.nombreArchivo("07", nota.getSerie(), nota.getNumero());
        return enviar(nombre, () -> xml.notaCredito(nota, certificado()));
    }

    private RespuestaOse enviar(String nombre, java.util.function.Supplier<byte[]> armarXml) {
        byte[] xmlFirmado = null;
        try {
            xmlFirmado = armarXml.get();
            SunatSoapClient.Cdr cdr = sunat.sendBill(nombre, xmlFirmado);
            String detalle = cdr.descripcion() + (cdr.observaciones() != null ? " (" + cdr.observaciones() + ")" : "");
            Resultado resultado = clasificar(cdr.codigo());
            switch (resultado) {
                case ACEPTADO, OBSERVADO -> log.info("SUNAT aceptó {}: {} - {}", nombre, cdr.codigo(), detalle);
                case RECHAZADO -> log.warn("SUNAT rechazó {}: {} - {}", nombre, cdr.codigo(), detalle);
                case REINTENTAR -> log.warn("SUNAT no pudo procesar {} ahora (se reintentará): {} - {}", nombre, cdr.codigo(), detalle);
                case POR_CONFIRMAR -> log.warn("SUNAT ya tenía registrado {} por un envío anterior (no se sabe si fue aceptado): {} - {}",
                        nombre, cdr.codigo(), detalle);
            }
            if (cdr.codigo() == YA_REGISTRADO) {
                detalle = "SUNAT ya tiene registrado este número por un envío anterior y no indica si fue aceptado o "
                        + "rechazado. Verifíquelo en SUNAT (Consulta de validez del comprobante) antes de entregarlo al "
                        + "cliente. Respuesta de SUNAT: " + detalle;
            }
            return new RespuestaOse(resultado, cdr.codigo(), detalle, null, null, xmlFirmado, cdr.zipCdr());
        } catch (SunatSoapClient.SunatNoDisponibleException e) {
            log.warn("No se pudo enviar {} a SUNAT (se reintentará): {}", nombre, e.getMessage());
            return RespuestaOse.reintentar(e.getMessage(), xmlFirmado);
        }
    }

    /**
     * SUNAT ya tiene registrado ese número por un envío anterior (p. ej. se perdió la respuesta del primer envío,
     * o se reenvió con el mismo número un comprobante que SUNAT había rechazado). No trae CDR: el envío anterior
     * pudo ser aceptado o rechazado, así que NO se puede dar por aceptado (informe de rechazos SUNAT, D-1).
     */
    private static final int YA_REGISTRADO = 1033;

    /**
     * Códigos de SUNAT: 0 aceptado; 4000+ aceptado con observaciones; 2000-3999 rechazo;
     * 0100-0199 fallas del sistema de SUNAT (temporales); 1033 ya registrado (por confirmar); resto de
     * 0200-1999, excepciones por datos o configuración que hay que corregir.
     */
    static Resultado clasificar(int codigo) {
        if (codigo == YA_REGISTRADO) return Resultado.POR_CONFIRMAR;
        if (codigo == 0) return Resultado.ACEPTADO;
        if (codigo >= 4000) return Resultado.OBSERVADO;
        if (codigo >= 100 && codigo <= 199) return Resultado.REINTENTAR;
        if (codigo < 0) return Resultado.REINTENTAR;
        return Resultado.RECHAZADO;
    }

    /** Se carga al primer envío: el backend arranca aunque falte el certificado y lo dice al emitir. */
    private synchronized CertificadoSunat certificado() {
        if (certificado == null) {
            certificado = CertificadoSunat.cargar(props.certificadoRuta(), props.certificadoClave());
        }
        return certificado;
    }
}
