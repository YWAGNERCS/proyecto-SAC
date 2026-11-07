package com.bomerp.facturacion.sunat.integration;

import com.bomerp.config.empresa.EmpresaProperties;
import com.bomerp.facturacion.comprobante.entity.Comprobante;
import com.bomerp.facturacion.notacredito.entity.NotaCredito;
import com.bomerp.facturacion.ose.integration.OseClient;
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
        String nombre = xml.nombreArchivo(XmlComprobante.tipoDocumento(comprobante), comprobante.getSerie(), comprobante.getNumero());
        return enviar(nombre, () -> xml.factura(comprobante, certificado()));
    }

    @Override
    public RespuestaOse emitirNotaCredito(NotaCredito nota) {
        String nombre = xml.nombreArchivo("07", nota.getSerie(), nota.getNumero());
        return enviar(nombre, () -> xml.notaCredito(nota, certificado()));
    }

    private RespuestaOse enviar(String nombre, java.util.function.Supplier<byte[]> armarXml) {
        try {
            SunatSoapClient.Cdr cdr = sunat.sendBill(nombre, armarXml.get());
            String detalle = cdr.codigo() + " - " + cdr.descripcion() + (cdr.observaciones() != null ? " (" + cdr.observaciones() + ")" : "");
            if (cdr.aceptado()) {
                log.info("SUNAT aceptó {}: {}", nombre, detalle);
                return new RespuestaOse(true, null, null, null);
            }
            log.warn("SUNAT rechazó {}: {}", nombre, detalle);
            return new RespuestaOse(false, null, null, detalle);
        } catch (SunatSoapClient.SunatNoDisponibleException e) {
            log.warn("No se pudo enviar {} a SUNAT: {}", nombre, e.getMessage());
            return new RespuestaOse(false, null, null, "SUNAT no disponible: " + e.getMessage());
        }
    }

    /** Se carga al primer envío: el backend arranca aunque falte el certificado y lo dice al emitir. */
    private synchronized CertificadoSunat certificado() {
        if (certificado == null) {
            certificado = CertificadoSunat.cargar(props.certificadoRuta(), props.certificadoClave());
        }
        return certificado;
    }
}
