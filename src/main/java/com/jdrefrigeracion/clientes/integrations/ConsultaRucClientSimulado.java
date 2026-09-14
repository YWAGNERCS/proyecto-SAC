package com.jdrefrigeracion.clientes.integrations;
import com.jdrefrigeracion.clientes.services.ClienteService;

/**
 * Implementación TEMPORAL/simulada, solo para poder correr y probar
 * el sistema mientras se decide y conecta un proveedor real de
 * consulta de RUC (ej. APIs Perú, Factiliza, SUNAT).
 *
 * Al ser una implementación de la interfaz ConsultaRucClient, el día
 * que se reemplace por la real, NO hay que tocar ClienteService
 * (ver Principio D - Inversión de Dependencias).
 */
public class ConsultaRucClientSimulado implements ConsultaRucClient {

    @Override
    public DatosRuc consultar(String ruc) {
        // Datos de ejemplo, para pruebas
        return new DatosRuc(ruc, "EMPRESA DE PRUEBA S.A.C.", "AV. EJEMPLO 123, LIMA");
    }

    @Override
    public DatosDni consultarDni(String dni) {
        return new DatosDni(dni, "JUAN", "PEREZ", "PRUEBA");
    }
}
