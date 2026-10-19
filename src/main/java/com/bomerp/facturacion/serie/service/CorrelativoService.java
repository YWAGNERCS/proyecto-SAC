package com.bomerp.facturacion.serie.service;

import com.bomerp.facturacion.comprobante.repository.ComprobanteRepository;
import com.bomerp.facturacion.notacredito.repository.NotaCreditoRepository;
import com.bomerp.facturacion.serie.entity.SerieComprobante;
import com.bomerp.facturacion.serie.repository.SerieComprobanteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Asigna el número correlativo de cada serie (SUNAT: empieza en 1, sin saltos ni repetidos).
 *
 * Corre dentro de la transacción de quien emite el comprobante (MANDATORY):
 *  - el bloqueo del contador dura hasta que esa venta termina, así dos ventas simultáneas
 *    no pueden tomar el mismo número (la segunda espera);
 *  - si la venta falla y se revierte, el contador también se revierte: no queda un salto.
 */
@Service
public class CorrelativoService {

    private final SerieComprobanteRepository serieRepository;
    private final ComprobanteRepository comprobanteRepository;
    private final NotaCreditoRepository notaCreditoRepository;

    public CorrelativoService(SerieComprobanteRepository serieRepository,
                              ComprobanteRepository comprobanteRepository,
                              NotaCreditoRepository notaCreditoRepository) {
        this.serieRepository = serieRepository;
        this.comprobanteRepository = comprobanteRepository;
        this.notaCreditoRepository = notaCreditoRepository;
    }

    /** Siguiente número de la serie, con 8 dígitos (ej. "00000001"). */
    @Transactional(propagation = Propagation.MANDATORY)
    public String siguiente(String serie) {
        SerieComprobante contador = serieRepository.bloquear(serie).orElseGet(() -> crear(serie));

        // Si alguien insertó comprobantes a mano (scripts, pruebas), el contador se pone al día
        long ultimoEmitido = ultimoNumeroEmitido(serie);
        long siguiente = Math.max(contador.getUltimoNumero(), ultimoEmitido) + 1;

        contador.setUltimoNumero(siguiente);
        serieRepository.save(contador);
        return String.format("%08d", siguiente);
    }

    /** Primera emisión de una serie: el contador arranca desde lo ya emitido (o desde 0). */
    private SerieComprobante crear(String serie) {
        serieRepository.saveAndFlush(new SerieComprobante(serie, ultimoNumeroEmitido(serie)));
        return serieRepository.bloquear(serie).orElseThrow();
    }

    private long ultimoNumeroEmitido(String serie) {
        String max = serie.length() == 4 && serie.charAt(1) == 'C'          // FC01 / BC01 = notas de crédito
                ? notaCreditoRepository.maxNumeroDeSerie(serie)
                : comprobanteRepository.maxNumeroDeSerie(serie);
        return max != null ? Long.parseLong(max) : 0L;
    }
}
