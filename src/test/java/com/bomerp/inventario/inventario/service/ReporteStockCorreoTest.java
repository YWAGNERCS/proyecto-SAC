package com.bomerp.inventario.inventario.service;

import com.bomerp.catalogo.producto.entity.Producto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReporteStockCorreoTest {
    @Test
    void distingueElStockAlMinimoYOrdenaPrimeroLosProductosPorDebajo() {
        Producto bomba = producto(25L, "Bomba de vacío", 1, 1);
        Producto capacitor = producto(27L, "Capacitor", 15, 15);
        Producto material = producto(403L, "Material <repuesto>", 9, 10);

        String html = ReporteStockCorreo.generar(List.of(bomba, capacitor, material));

        assertThat(html)
                .contains("Productos en el mínimo o por debajo.", "src=\"cid:logo\"")
                .containsSubsequence(">1</strong>", "Por debajo del mínimo", ">2</strong>", "En el mínimo")
                .containsSubsequence("Material &lt;repuesto&gt;", ">9</strong>", ">10</td>", ">Bajo mínimo</strong>",
                        "Bomba de vacío", ">En el mínimo</strong>", "Capacitor")
                .doesNotContain("Material <repuesto>", "cid:whatsapp");
    }

    private static Producto producto(Long id, String nombre, int actual, int minimo) {
        Producto producto = new Producto();
        producto.setId(id);
        producto.setNombre(nombre);
        producto.setStockActual(actual);
        producto.setStockMinimo(minimo);
        return producto;
    }
}
