package com.bomerp.facturacion.comprobante.repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.bomerp.facturacion.comprobante.entity.Comprobante;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ComprobanteRepository extends JpaRepository<Comprobante, Long> {
    Optional<Comprobante> findByVentaId(Long ventaId);

    List<Comprobante> findByVenta_IdIn(List<Long> ventaIds);

    /**
     * Pendientes que el reintento automático puede volver a enviar, los más antiguos primero. Se filtran aquí,
     * antes del límite del lote, los que agotaron sus intentos (F-02) y los que SUNAT respondió con el código
     * "ya registrado" (1033): esos quedan por confirmar y reenviarlos solo repetiría la misma respuesta.
     */
    @Query("SELECT c FROM Comprobante c WHERE c.estadoSunat = :estado AND c.envio.intentos < :maxIntentos"
            + " AND (c.envio.codigo IS NULL OR c.envio.codigo <> :codigoPorConfirmar) ORDER BY c.id")
    List<Comprobante> pendientesParaReintentar(@Param("estado") Comprobante.EstadoSunat estado,
                                               @Param("maxIntentos") int maxIntentos,
                                               @Param("codigoPorConfirmar") int codigoPorConfirmar,
                                               org.springframework.data.domain.Limit lote);

    /** Último número emitido en una serie (los números tienen 8 dígitos, así que MAX de texto sirve). */
    @Query("SELECT MAX(c.numero) FROM Comprobante c WHERE c.serie = :serie")
    String maxNumeroDeSerie(@Param("serie") String serie);
}
