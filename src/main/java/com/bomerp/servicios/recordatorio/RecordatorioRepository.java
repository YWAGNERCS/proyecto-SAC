package com.bomerp.servicios.recordatorio;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
import java.time.LocalDate;
public interface RecordatorioRepository extends JpaRepository<RecordatorioServicio,Long> {
    Optional<RecordatorioServicio> findByOrden_Id(Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RecordatorioServicio r where r.id=:id") Optional<RecordatorioServicio> bloquear(@Param("id") Long id);
    @Query("""
        select r.id from RecordatorioServicio r where r.activo=true and r.fechaAviso<=:hoy and (
          (r.estadoCliente in ('PENDIENTE','FALLIDO') and r.intentosCliente<3 and (r.intentoCliente is null or r.intentoCliente<:inicio)) or
          (r.estadoOficina in ('PENDIENTE','FALLIDO') and r.intentosOficina<3 and (r.intentoOficina is null or r.intentoOficina<:inicio)) or
          (r.estadoCliente='ENVIANDO' and r.intentoCliente<:vencido) or
          (r.estadoOficina='ENVIANDO' and r.intentoOficina<:vencido)) order by r.fechaAviso,r.id
        """)
    List<Long> pendientes(@Param("hoy") LocalDate hoy,@Param("inicio") java.time.LocalDateTime inicio,
                         @Param("vencido") java.time.LocalDateTime vencido,org.springframework.data.domain.Pageable limite);
}
