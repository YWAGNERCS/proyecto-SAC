package com.bomerp.seguridad.auditoria.repository;

import com.bomerp.seguridad.auditoria.entity.RegistroCambio;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface RegistroCambioRepository extends JpaRepository<RegistroCambio, Long> {

    /** Historial, los más recientes primero; cada filtro es opcional (null = no filtra). */
    @Query("""
            SELECT r FROM RegistroCambio r
            WHERE (:usuario IS NULL OR r.usuario = :usuario)
              AND (:modulo IS NULL OR r.modulo = :modulo)
              AND (:resultado IS NULL OR r.resultado = :resultado)
              AND (CAST(:desde AS LocalDateTime) IS NULL OR r.fechaHora >= :desde)
              AND (CAST(:hasta AS LocalDateTime) IS NULL OR r.fechaHora < :hasta)
            ORDER BY r.fechaHora DESC, r.id DESC
            """)
    List<RegistroCambio> buscar(@Param("usuario") String usuario, @Param("modulo") String modulo,
                                @Param("resultado") RegistroCambio.Resultado resultado,
                                @Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta,
                                Pageable pagina);

    @Query("SELECT DISTINCT r.usuario FROM RegistroCambio r ORDER BY r.usuario")
    List<String> usuarios();

    @Query("SELECT DISTINCT r.modulo FROM RegistroCambio r ORDER BY r.modulo")
    List<String> modulos();
}
