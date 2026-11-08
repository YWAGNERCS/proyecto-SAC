package com.bomerp.facturacion.comprobante.repository;

import com.bomerp.facturacion.comprobante.entity.EnvioSunatRegistro;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Historial de respuestas de SUNAT (tabla ENVIOS_SUNAT): solo se agregan filas. */
public interface EnvioSunatRegistroRepository extends JpaRepository<EnvioSunatRegistro, Long> {

    List<EnvioSunatRegistro> findByComprobanteIdOrderByIdDesc(Long comprobanteId);

    List<EnvioSunatRegistro> findByNotaCreditoIdOrderByIdDesc(Long notaCreditoId);
}
