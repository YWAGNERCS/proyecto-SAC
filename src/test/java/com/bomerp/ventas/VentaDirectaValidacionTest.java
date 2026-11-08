package com.bomerp.ventas;

import com.bomerp.config.GlobalExceptionHandler;
import com.bomerp.ventas.reporte.service.VentaReporteService;
import com.bomerp.ventas.venta.controller.VentaController;
import com.bomerp.ventas.venta.service.VentaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Auditoría B9: POST /ventas/directa aplica las restricciones del cuerpo. Antes, sin @Valid, una llamada sin
 * tipoPago se registraba como contado sin avisar. Sin Oracle ni Keycloak: el servicio es un mock y no se llama.
 */
class VentaDirectaValidacionTest {

    private VentaService ventaService;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        ventaService = mock(VentaService.class);
        LocalValidatorFactoryBean validador = new LocalValidatorFactoryBean();
        validador.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new VentaController(ventaService, mock(VentaReporteService.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validador)
                .build();
    }

    private void rechaza(String cuerpo, String campo, String mensaje) throws Exception {
        mvc.perform(post("/api/v1/admin/ventas/directa").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores['" + campo + "']").value(mensaje));
    }

    @Test
    void sinFormaDePagoResponde400EnVezDeRegistrarContado() throws Exception {
        rechaza("""
                {"clienteId": 1, "items": [{"productoId": 5, "cantidad": 1}]}""",
                "tipoPago", "Indique la forma de pago (CONTADO o CREDITO).");
        verify(ventaService, never()).registrarVentaDirecta(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void sinClienteNiProductosResponde400() throws Exception {
        rechaza("""
                {"items": [{"productoId": 5, "cantidad": 1}], "tipoPago": "CONTADO"}""",
                "clienteId", "Indique el cliente.");
        rechaza("""
                {"clienteId": 1, "items": [], "tipoPago": "CONTADO"}""",
                "items", "La venta necesita al menos un producto.");
    }

    @Test
    void cadaLineaNecesitaProductoYCantidadPositiva() throws Exception {
        rechaza("""
                {"clienteId": 1, "items": [{"cantidad": 1}], "tipoPago": "CONTADO"}""",
                "items[0].productoId", "Cada línea necesita un producto.");
        rechaza("""
                {"clienteId": 1, "items": [{"productoId": 5, "cantidad": 0}], "tipoPago": "CONTADO"}""",
                "items[0].cantidad", "La cantidad debe ser mayor que cero.");
        verify(ventaService, never()).registrarVentaDirecta(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }
}
