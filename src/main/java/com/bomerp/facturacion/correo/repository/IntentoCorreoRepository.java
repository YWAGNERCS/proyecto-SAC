package com.bomerp.facturacion.correo.repository;

import com.bomerp.facturacion.correo.entity.IntentoCorreo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface IntentoCorreoRepository extends JpaRepository<IntentoCorreo, Long> {

    List<IntentoCorreo> findByEnvioCorreoIdInOrderByIdDesc(Collection<Long> envioCorreoIds);
}
