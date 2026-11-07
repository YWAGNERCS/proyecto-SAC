package com.bomerp.config.paginacion;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Orden por columna que piden las pantallas paginadas (Ventas, Proformas, Compras). */
class OrdenTest {

    record Fila(long id, String cliente, BigDecimal total) {
    }

    private static final List<Fila> FILAS = List.of(
            new Fila(1, "Zegarra", new BigDecimal("100.00")),
            new Fila(2, "ábaco SAC", new BigDecimal("2500.50")),
            new Fila(3, "Bodega Lucía", null),
            new Fila(4, "Abaco SAC", new BigDecimal("80")));

    private static final Map<String, Orden.Campo<Fila>> CAMPOS = Map.of(
            "cliente", Orden.texto(Fila::cliente),
            "total", Orden.valor(Fila::total));

    private static final Comparator<Fila> POR_DEFECTO = Comparator.comparing(Fila::id).reversed();

    private static List<Long> ids(String orden) {
        return Orden.ordenar(FILAS, orden, CAMPOS, POR_DEFECTO).stream().map(Fila::id).toList();
    }

    @Test
    void textoAlfabeticoSinDistinguirTildesNiMayusculas() {
        // "ábaco SAC" y "Abaco SAC" empatan: desempata el orden por defecto (id mayor primero)
        assertThat(ids("cliente,asc")).containsExactly(4L, 2L, 3L, 1L);
        assertThat(ids("cliente,desc")).containsExactly(1L, 3L, 4L, 2L);
    }

    @Test
    void numerosDeMenorAMayorConLosVaciosAlFinal() {
        assertThat(ids("total,asc")).containsExactly(4L, 1L, 2L, 3L);
        assertThat(ids("total")).containsExactly(4L, 1L, 2L, 3L);
        assertThat(ids("total,desc")).containsExactly(2L, 1L, 4L, 3L);
    }

    @Test
    void losMontosEnDolaresSeComparanEnSoles() {
        BigDecimal tcVenta = new BigDecimal("3.70"), tcHoy = new BigDecimal("3.80");
        assertThat(Orden.enSoles(new BigDecimal("100"), true, tcVenta, tcHoy)).isEqualByComparingTo("370");
        assertThat(Orden.enSoles(new BigDecimal("100"), true, null, tcHoy)).isEqualByComparingTo("380");
        assertThat(Orden.enSoles(new BigDecimal("100"), false, tcVenta, tcHoy)).isEqualByComparingTo("100");
        assertThat(Orden.enSoles(new BigDecimal("100"), true, null, null)).isEqualByComparingTo("100");
        assertThat(Orden.pide("total,desc", "total")).isTrue();
        assertThat(Orden.pide("cliente,asc", "total")).isFalse();
        assertThat(Orden.pide(null, "total")).isFalse();
    }

    @Test
    void sinOrdenOConUnCampoDesconocidoQuedaComoVino() {
        assertThat(ids(null)).containsExactly(1L, 2L, 3L, 4L);
        assertThat(ids(" ")).containsExactly(1L, 2L, 3L, 4L);
        assertThat(ids("contrasena,asc")).containsExactly(1L, 2L, 3L, 4L);
    }
}
