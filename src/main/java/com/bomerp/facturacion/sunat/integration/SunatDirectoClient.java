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

    /**
     * Una factura con detracción sin la cuenta del Banco de la Nación la rechaza SUNAT (3034). Es un dato que
     * le falta a JD, no a la venta: el comprobante no se crea (ni toma número) hasta que se configure.
     */
    @Override
    public String motivoParaNoEmitir(Venta venta) {
        if (venta.getTipoDeduccion() == Venta.TipoDeduccion.DETRACCION
                && (empresa.cuentaDetracciones() == null || empresa.cuentaDetracciones().isBlank())) {
            return "Falta configurar la cuenta de detracciones de JD en el Banco de la Nación (CUENTA_DETRACCIONES). "
                    + "El comprobante se emite solo cuando esté configurada; mientras tanto no usa número ni corre su plazo.";
        }
        return null;
    }

    @Override
    public RespuestaOse emitir(Comprobante comprobante) {
        // Comprobantes que ya tenían número cuando faltaba la cuenta (antes del 07/11/2026): no se envían y
        // quedan PENDIENTES; al configurarla, el reintento automático los envía (si siguen en plazo).
        if (motivoParaNoEmitir(comprobante.getVenta()) != null) {
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
                case CREDENCIALES -> log.error("SUNAT rechazó el usuario o la clave SOL al enviar {}: {} - {}", nombre, cdr.codigo(), detalle);
            }
            if (resultado == Resultado.CREDENCIALES) {
                detalle = "SUNAT rechazó el usuario o la clave SOL (código " + cdr.codigo() + "). Se detuvieron los envíos "
                        + "para no bloquear la cuenta: corrija SUNAT_USUARIO y SUNAT_CLAVE y reinicie el backend. "
                        + "Respuesta de SUNAT: " + detalle;
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
     * 0101-0106 usuario o clave SOL rechazados (encabezado de seguridad, usuario o clave incorrectos, usuario
     * inexistente, inactivo o no válido); el resto de 0100-0199, fallas del sistema de SUNAT (temporales);
     * 1033 ya registrado (por confirmar); resto de 0200-1999, excepciones por datos o configuración que hay que corregir.
     */
    static Resultado clasificar(int codigo) {
        if (codigo == YA_REGISTRADO) return Resultado.POR_CONFIRMAR;
        if (codigo >= 101 && codigo <= 106) return Resultado.CREDENCIALES;
        if (codigo == 0) return Resultado.ACEPTADO;
        if (codigo >= 4000) return Resultado.OBSERVADO;
        if (codigo >= 100 && codigo <= 199) return Resultado.REINTENTAR;
        if (codigo < 0) return Resultado.REINTENTAR;
        return Resultado.RECHAZADO;
    }

    /** Solo la SUNAT de producción da validez tributaria; la beta es de pruebas. */
    @Override
    public boolean conValidezTributaria() {
        return ambiente() == com.bomerp.facturacion.comprobante.entity.Comprobante.AmbienteEmision.PRODUCCION;
    }

    /** La URL decide el ambiente: la de pruebas de SUNAT contiene "beta". */
    @Override
    public com.bomerp.facturacion.comprobante.entity.Comprobante.AmbienteEmision ambiente() {
        return props.url() != null && !props.url().toLowerCase().contains("beta")
                ? com.bomerp.facturacion.comprobante.entity.Comprobante.AmbienteEmision.PRODUCCION
                : com.bomerp.facturacion.comprobante.entity.Comprobante.AmbienteEmision.BETA;
    }

    /** Se carga al primer envío: el backend arranca aunque falte el certificado y lo dice al emitir. */
    private synchronized CertificadoSunat certificado() {
        if (certificado == null) {
            certificado = CertificadoSunat.cargar(props.certificadoRuta(), props.certificadoClave());
        }
        return certificado;
    }
}
