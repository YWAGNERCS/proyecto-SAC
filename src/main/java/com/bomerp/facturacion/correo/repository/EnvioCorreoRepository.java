package com.bomerp.facturacion.correo.repository;

import com.bomerp.facturacion.correo.entity.EnvioCorreo;
import com.bomerp.facturacion.correo.entity.EnvioCorreo.Estado;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface EnvioCorreoRepository extends JpaRepository<EnvioCorreo, Long> {

    List<EnvioCorreo> findByComprobanteIdOrderByIdDesc(Long comprobanteId);

    List<EnvioCorreo> findByComprobanteIdInOrderByIdDesc(Collection<Long> comprobanteIds);

    boolean existsByComprobanteIdAndTipo(Long comprobanteId, EnvioCorreo.Tipo tipo);

    /** El comprobante ya tiene algún envío por correo (automático o manual, en cualquier estado). */
    boolean existsByComprobanteId(Long comprobanteId);

    /** FALLIDOS que esperan su reintento automático. */
    List<EnvioCorreo> findByComprobanteIdAndEstadoAndProximoIntentoIsNotNull(Long comprobanteId, Estado estado);

    boolean existsByComprobanteIdAndEstadoIn(Long comprobanteId, Collection<Estado> estados);

    /**
     * Toma el envío para un trabajador: pasa a EN_PROCESO solo si sigue PENDIENTE, o FALLIDO con su hora de
     * reintento cumplida. Devuelve 1 si lo tomó y 0 si otro ya lo tenía o ya no corresponde (nunca dos a la vez).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE EnvioCorreo e
               SET e.estado = com.bomerp.facturacion.correo.entity.EnvioCorreo.Estado.EN_PROCESO,
                   e.enProcesoDesde = :ahora, e.fechaUltimoIntento = :ahora, e.intentos = e.intentos + 1,
                   e.messageId = :messageId, e.proximoIntento = NULL
             WHERE e.id = :id
               AND (e.estado = com.bomerp.facturacion.correo.entity.EnvioCorreo.Estado.PENDIENTE
                    OR (e.estado = com.bomerp.facturacion.correo.entity.EnvioCorreo.Estado.FALLIDO
                        AND e.proximoIntento IS NOT NULL AND e.proximoIntento <= :ahora))
            """)
    int tomar(@Param("id") Long id, @Param("ahora") LocalDateTime ahora, @Param("messageId") String messageId);

    /** Pendientes y fallidos cuya hora de reintento llegó, los más antiguos primero. */
    @Query("""
            SELECT e.id FROM EnvioCorreo e
             WHERE e.estado = com.bomerp.facturacion.correo.entity.EnvioCorreo.Estado.PENDIENTE
                OR (e.estado = com.bomerp.facturacion.correo.entity.EnvioCorreo.Estado.FALLIDO
                    AND e.proximoIntento IS NOT NULL AND e.proximoIntento <= :ahora)
             ORDER BY e.id
            """)
    List<Long> porProcesar(@Param("ahora") LocalDateTime ahora, Limit limite);

    /**
     * Comprobantes aceptados (u observados) por SUNAT en PRODUCCION, entre esas horas, vigentes, de un cliente con
     * correo y SIN NINGÚN envío por correo (ni automático ni manual, en ningún estado): los que la conciliación tiene que
     * recuperar. Si Lisandro ya lo envió a mano, no se le crea otro.
     */
    @Query("""
            SELECT c.id FROM Comprobante c JOIN c.venta v JOIN v.cliente cl
             WHERE c.reemplazado = false
               AND c.estadoSunat IN (com.bomerp.facturacion.comprobante.entity.Comprobante.EstadoSunat.ACEPTADO,
                                     com.bomerp.facturacion.comprobante.entity.Comprobante.EstadoSunat.OBSERVADO)
               AND c.ambienteEmision = com.bomerp.facturacion.comprobante.entity.Comprobante.AmbienteEmision.PRODUCCION
               AND c.envio.ultimoIntento >= :desde AND c.envio.ultimoIntento <= :hasta
               AND cl.correo IS NOT NULL
               AND NOT EXISTS (SELECT 1 FROM EnvioCorreo e WHERE e.comprobanteId = c.id)
             ORDER BY c.id
            """)
    List<Long> aceptadosSinEnvioAutomatico(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta,
                                          Limit limite);

    /** EN_PROCESO desde antes de esa hora: el proceso que los tomó se interrumpió. */
    List<EnvioCorreo> findByEstadoAndEnProcesoDesdeBefore(Estado estado, LocalDateTime limite);
}
