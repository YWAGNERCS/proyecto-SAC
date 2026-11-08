package com.bomerp.facturacion.comprobante.dto;

import com.bomerp.facturacion.comprobante.entity.Comprobante;
import com.bomerp.facturacion.comprobante.entity.EnvioSunat;
import com.bomerp.facturacion.notacredito.entity.NotaCredito;
import com.bomerp.ventas.venta.entity.Venta;

import java.util.List;

/**
 * Estado ante SUNAT del comprobante vigente de una venta y, si se anuló, de su nota de crédito. Lo pide la
 * lista de Ventas para mostrar si cada documento fue aceptado, observado, rechazado o sigue pendiente.
 *
 * @param reemplazados comprobantes anteriores de la misma venta que SUNAT rechazó con CDR y se sustituyeron
 *                     (historial de emisiones), del más antiguo al más reciente; vacío si no hubo reemplazos
 * @param porEmitir    la venta todavía no tiene comprobante porque falta un dato de JD (p. ej. la cuenta de
 *                     detracciones): el motivo. Entonces comprobante es null. Null si ya tiene comprobante.
 * @param correo       entrega del comprobante vigente por correo al cliente (estado, destinatario, intentos); null
 *                     si la venta todavía no tiene comprobante
 */
public record EstadoSunatVentaDTO(Long ventaId, Documento comprobante, Documento notaCredito, List<Documento> reemplazados,
                                  String porEmitir, com.bomerp.facturacion.correo.dto.EntregaCorreoDTO correo) {

    public static EstadoSunatVentaDTO porEmitir(Long ventaId, String motivo) {
        return new EstadoSunatVentaDTO(ventaId, null, null, List.of(), motivo, null);
    }

    /**
     * @param tieneXml          hay XML firmado guardado (para descargarlo)
     * @param tieneCdr          hay constancia de recepción de SUNAT guardada
     * @param simulado          "aceptado" por el envío simulado: nunca llegó a SUNAT y no tiene validez tributaria
     * @param rechazoDefinitivo SUNAT lo rechazó con CDR: su número quedó usado y no puede reenviarse
     * @param puedeReemplazar   rechazo definitivo, fuera de plazo o tipo que ya no corresponde al cliente (D-11) de un
     *                          comprobante (no nota) vigente de una venta no anulada: se puede emitir el reemplazo
     * @param fechaLimiteEnvio  último día para que SUNAT lo reciba, si todavía no lo registró (null si ya no aplica)
     * @param fueraDePlazo      pasó esa fecha sin que SUNAT lo registrara: ya no se envía (informe D-6)
     */
    public record Documento(Long id, String numero, String estado, Integer codigo, String mensaje,
                            int intentos, boolean tieneXml, boolean tieneCdr, boolean simulado,
                            boolean rechazoDefinitivo, boolean puedeReemplazar,
                            java.time.LocalDate fechaLimiteEnvio, boolean fueraDePlazo) {

        static Documento de(Long id, String serie, String numero, Comprobante.EstadoSunat estado, EnvioSunat envio,
                            boolean rechazoDefinitivo, boolean puedeReemplazar,
                            java.time.LocalDate fechaLimiteEnvio, boolean fueraDePlazo) {
            // Simulado: los emitidos antes del envío real (aceptados sin ningún intento) o por el modo simulado
            boolean simulado = estado.valido() && envio.getCdr() == null
                    && (envio.getIntentos() == 0 || (envio.getMensaje() != null && envio.getMensaje().startsWith("Simulado")));
            return new Documento(id, serie + "-" + Integer.parseInt(numero), estado.name(), envio.getCodigo(), envio.getMensaje(),
                    envio.getIntentos(), envio.getXmlFirmado() != null, envio.getCdr() != null, simulado,
                    rechazoDefinitivo, puedeReemplazar, fechaLimiteEnvio, fueraDePlazo);
        }

        static Documento de(Comprobante c) {
            boolean ventaActiva = c.getVenta() == null || c.getVenta().getEstado() != Venta.EstadoVenta.ANULADA;
            boolean fueraDePlazo = c.fueraDePlazo(java.time.LocalDate.now());
            return de(c.getId(), c.getSerie(), c.getNumero(), c.getEstadoSunat(), c.getEnvio(), c.rechazoDefinitivo(),
                    (c.rechazoDefinitivo() || fueraDePlazo || c.necesitaOtroTipo()) && !c.isReemplazado() && ventaActiva,
                    c.faltaQueSunatLoRegistre() ? c.fechaLimiteEnvio() : null, fueraDePlazo);
        }

        static Documento de(NotaCredito n) {
            Integer codigo = n.getEnvio().getCodigo();
            boolean definitivo = n.getEstadoSunat() == Comprobante.EstadoSunat.RECHAZADO && codigo != null
                    && ((codigo >= 2000 && codigo <= 3999) || codigo == Comprobante.CODIGO_YA_RECHAZADO);
            return de(n.getId(), n.getSerie(), n.getNumero(), n.getEstadoSunat(), n.getEnvio(), definitivo, false, null, false);
        }
    }

    public static EstadoSunatVentaDTO de(Comprobante c, NotaCredito nota, List<Comprobante> reemplazados) {
        return de(c, nota, reemplazados, null);
    }

    public static EstadoSunatVentaDTO de(Comprobante c, NotaCredito nota, List<Comprobante> reemplazados,
                                         com.bomerp.facturacion.correo.dto.EntregaCorreoDTO correo) {
        return new EstadoSunatVentaDTO(c.getVenta().getId(), Documento.de(c),
                nota == null ? null : Documento.de(nota),
                reemplazados.stream().map(Documento::de).toList(), null, correo);
    }
}
