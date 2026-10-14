package com.bomerp.clientes.consultaruc.integration;

import org.springframework.stereotype.Component;

/**
 * Datos de prueba para trabajar SIN internet. Solo se usa si se activa
 * app.consulta-documentos.simulado=true (variable CONSULTA_DOCUMENTOS_SIMULADO).
 * Ver ConsultaRucClientConRespaldo.
 */
@Component
public class ConsultaRucClientSimulado implements ConsultaRucClient {

    @Override
    public DatosRuc consultar(String ruc) {
        return new DatosRuc(ruc, "EMPRESA DE PRUEBA S.A.C.", "AV. EJEMPLO 123, LIMA");
    }

    @Override
    public DatosDni consultarDni(String dni) {
        return new DatosDni(dni, "JUAN", "PEREZ", "PRUEBA");
    }
}
