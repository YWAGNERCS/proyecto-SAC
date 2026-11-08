package com.bomerp.config;

import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.compras.compra.entity.Compra;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Auditoría M1 y M2: precio editado de una línea, IGV de la compra y último costo del producto. */
class PrecioDeLineaTest {

    private static final BigDecimal CATALOGO = new BigDecimal("525");

    private static PrecioDeLinea resolver(String pedido, String motivo, boolean puede) {
        return PrecioDeLinea.resolver(CATALOGO, pedido == null ? null : new BigDecimal(pedido), motivo, "R32", "S/",
                puede, "lucy");
    }

    @Test
    void sinPrecioOConElDeCatalogoNoEsUnaEdicion() {
        assertThat(resolver(null, null, false).editado()).isFalse();
        PrecioDeLinea igual = resolver("525.001", null, false);
        assertThat(igual.editado()).isFalse();
        assertThat(igual.precioUnitario()).isEqualByComparingTo("525.00");
    }

    @Test
    void subirConPermisoYMotivoGuardaLasDosCifras() {
        PrecioDeLinea p = resolver("560", "  Subió el costo del proveedor  ", true);
        assertThat(p.editado()).isTrue();
        assertThat(p.precioUnitario()).isEqualByComparingTo("560.00");
        assertThat(p.precioCatalogo()).isEqualByComparingTo("525.00");
        assertThat(p.editadoPor()).isEqualTo("lucy");
        assertThat(p.motivo()).isEqualTo("Subió el costo del proveedor");
        assertThat(p.paraHistorial("R32", "S/"))
                .isEqualTo("R32: S/ 525.00 → S/ 560.00 (motivo: Subió el costo del proveedor)");
        // Leído de Oracle llega sin decimales (525): el historial igual muestra 525.00
        assertThat(new PrecioDeLinea(new BigDecimal("550"), new BigDecimal("525"), "lucy", "Pactado").paraHistorial("R32", "S/"))
                .isEqualTo("R32: S/ 525.00 → S/ 550.00 (motivo: Pactado)");
    }

    @Test
    void noSePuedeBajarNiEditarSinPermisoNiSinMotivo() {
        assertThatThrownBy(() -> resolver("524.99", "Cliente frecuente", true))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("use el descuento");
        assertThatThrownBy(() -> resolver("560", "Subió el costo", false))
                .isInstanceOf(PrecioDeLinea.SinPermisoException.class);
        assertThatThrownBy(() -> resolver("560", "caro", true))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("de 5 a 200 caracteres");
        assertThatThrownBy(() -> resolver("560", "x".repeat(201), true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void laCompraSeparaElIgvDelTotalPagado() {
        Compra compra = new Compra();
        compra.fijarTotalConIgv(new BigDecimal("1000"));
        assertThat(compra.getTotal()).isEqualByComparingTo("1000.00");
        assertThat(compra.getSubtotal()).isEqualByComparingTo("847.46");
        assertThat(compra.getIgv()).isEqualByComparingTo("152.54");
        assertThat(compra.getSubtotal().add(compra.getIgv())).isEqualByComparingTo(compra.getTotal());
    }

    @Test
    void elProductoGuardaElCostoDeLaCompraMasReciente() {
        Producto p = new Producto();
        p.setCostoBase(new BigDecimal("500"));
        assertThat(p.costoVigente()).isEqualByComparingTo("500");   // nunca se compró: costo base

        LocalDate hoy = LocalDate.of(2026, 11, 8);
        p.registrarCostoDeCompra(new BigDecimal("560"), hoy);
        assertThat(p.costoVigente()).isEqualByComparingTo("560.00");

        // Una compra con fecha anterior (registrada tarde) no pisa la más reciente
        p.registrarCostoDeCompra(new BigDecimal("480"), hoy.minusDays(5));
        assertThat(p.getUltimoCostoCompra()).isEqualByComparingTo("560.00");
        assertThat(p.getFechaUltimaCompra()).isEqualTo(hoy);

        p.registrarCostoDeCompra(new BigDecimal("570"), hoy);
        assertThat(p.getUltimoCostoCompra()).isEqualByComparingTo("570.00");
        assertThat(p.getCostoBase()).isEqualByComparingTo("500");   // el catálogo no cambia
    }
}
