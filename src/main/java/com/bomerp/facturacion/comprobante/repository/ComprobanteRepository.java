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
    /** El comprobante vigente de la venta: el que no fue reemplazado (hay uno como máximo, por índice único). */
    Optional<Comprobante> findByVentaIdAndReemplazadoFalse(Long ventaId);

    /** Todos los comprobantes de esas ventas, vigentes y reemplazados, en orden de emisión. */
    List<Comprobante> findByVenta_IdInOrderByIdAsc(List<Long> ventaIds);

    /** Bloquea el comprobante hasta el fin de la transacción: dos reemplazos simultáneos no pueden cruzarse. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.QueryHints(@jakarta.persistence.QueryHint(name = "jakarta.persistence.lock.timeout", value = "10000"))
    @Query("SELECT c FROM Comprobante c WHERE c.id = :id")
    Optional<Comprobante> bloquear(@Param("id") Long id);

    /**
     * Pendientes que el reintento automático puede volver a enviar, los más antiguos primero. Se filtran aquí,
     * antes del límite del lote, los que agotaron sus intentos (F-02) y los que SUNAT respondió con el código
     * "ya registrado" (1033): esos quedan por confirmar y reenviarlos solo repetiría la misma respuesta.
     */
    @Query("SELECT c FROM Comprobante c WHERE c.estadoSunat = :estado AND c.envio.intentos < :maxIntentos"
            + " AND (c.envio.codigo IS NULL OR c.envio.codigo <> :codigoPorConfirmar)"
            + " AND c.fechaEmision >= :emitidosDesde ORDER BY c.id")
    List<Comprobante> pendientesParaReintentar(@Param("estado") Comprobante.EstadoSunat estado,
                                               @Param("maxIntentos") int maxIntentos,
                                               @Param("codigoPorConfirmar") int codigoPorConfirmar,
                                               @Param("emitidosDesde") java.time.LocalDate emitidosDesde,
                                               org.springframework.data.domain.Limit lote);

    /** Comprobantes vigentes que SUNAT aún no tiene (pendientes o rechazados), para el aviso del plazo de envío. */
    List<Comprobante> findByReemplazadoFalseAndEstadoSunatIn(java.util.Collection<Comprobante.EstadoSunat> estados);

    /** Último número emitido en una serie (los números tienen 8 dígitos, así que MAX de texto sirve). */
    @Query("SELECT MAX(c.numero) FROM Comprobante c WHERE c.serie = :serie")
    String maxNumeroDeSerie(@Param("serie") String serie);
}
