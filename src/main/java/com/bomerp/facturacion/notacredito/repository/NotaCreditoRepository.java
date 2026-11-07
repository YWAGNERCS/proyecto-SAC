package com.bomerp.facturacion.notacredito.repository;

import com.bomerp.facturacion.comprobante.entity.Comprobante;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.bomerp.facturacion.notacredito.entity.NotaCredito;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotaCreditoRepository extends JpaRepository<NotaCredito, Long> {

    Optional<NotaCredito> findByComprobanteReferencia_Id(Long comprobanteId);

    List<NotaCredito> findByComprobanteReferencia_IdIn(List<Long> comprobanteIds);

    /** Igual que en ComprobanteRepository: sin los que agotaron sus intentos ni los "ya registrado" (1033). */
    @Query("SELECT n FROM NotaCredito n WHERE n.estadoSunat = :estado AND n.envio.intentos < :maxIntentos"
            + " AND (n.envio.codigo IS NULL OR n.envio.codigo <> :codigoPorConfirmar) ORDER BY n.id")
    List<NotaCredito> pendientesParaReintentar(@Param("estado") Comprobante.EstadoSunat estado,
                                               @Param("maxIntentos") int maxIntentos,
                                               @Param("codigoPorConfirmar") int codigoPorConfirmar,
                                               org.springframework.data.domain.Limit lote);

    /** Último número emitido en una serie de notas de crédito (FC01 / BC01). */
    @Query("SELECT MAX(n.numero) FROM NotaCredito n WHERE n.serie = :serie")
    String maxNumeroDeSerie(@Param("serie") String serie);

    List<NotaCredito> findAllByOrderByIdDesc();
}
