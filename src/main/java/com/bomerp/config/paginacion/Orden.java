package com.bomerp.config.paginacion;

import java.math.BigDecimal;
import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Orden que pide una pantalla al hacer clic en el encabezado de una columna: {@code "cliente,asc"} o
 * {@code "total,desc"}. Cada listado declara qué columnas se pueden ordenar; un campo que no está en esa
 * lista (o un texto vacío) deja el orden por defecto, así que nadie puede ordenar por un campo interno.
 *
 * <p>Se ordena antes de paginar, sobre todos los registros del filtro: la página 2 sigue el mismo orden
 * que la 1. Los vacíos van siempre al final, en las dos direcciones.
 */
public final class Orden {

    /** Español: sin distinguir mayúsculas ni tildes ("Ábaco" junto a "abaco"). */
    private static final Collator TEXTO = Collator.getInstance(Locale.of("es", "PE"));

    static {
        TEXTO.setStrength(Collator.PRIMARY);
    }

    /** Una columna ordenable: cómo leer su valor y cómo comparar dos valores (ascendente). */
    public record Campo<E>(Function<E, Object> leer, Comparator<Object> comparar) {
    }

    private Orden() {
    }

    /** Columna de texto: orden alfabético en español. */
    @SuppressWarnings("unchecked")
    public static <E> Campo<E> texto(Function<E, String> campo) {
        return new Campo<>((Function<E, Object>) (Function<E, ?>) campo, (a, b) -> TEXTO.compare((String) a, (String) b));
    }

    /** Columna de número o fecha: de menor a mayor (o de la más antigua a la más reciente). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <E, C extends Comparable<? super C>> Campo<E> valor(Function<E, C> campo) {
        return new Campo<>((Function<E, Object>) (Function<E, ?>) campo, (a, b) -> ((Comparable) a).compareTo(b));
    }

    /** ¿El orden pedido es por este campo? (para preparar algo solo si hace falta, p. ej. el tipo de cambio). */
    public static boolean pide(String orden, String campo) {
        return orden != null && orden.split(",", 2)[0].trim().equals(campo);
    }

    /**
     * Monto llevado a soles para comparar operaciones en monedas distintas: US$ 20 000 va por encima de
     * S/ 66 000. Usa el tipo de cambio de la propia operación o, si no lo tiene, el de hoy; sin ninguno
     * compara el número tal cual.
     */
    public static BigDecimal enSoles(BigDecimal monto, boolean enDolares, BigDecimal tipoCambio, BigDecimal tipoCambioHoy) {
        if (monto == null || !enDolares) return monto;
        BigDecimal tc = tipoCambio != null ? tipoCambio : tipoCambioHoy;
        return tc != null ? monto.multiply(tc) : monto;
    }

    /**
     * Ordena la lista según {@code orden} ("campo,asc" o "campo,desc"; sin dirección = asc).
     *
     * @param campos     columnas permitidas
     * @param porDefecto el orden de siempre: desempata (dos ventas del mismo cliente quedan por fecha)
     * @return la lista ordenada, o la misma lista si no se pidió un orden válido
     */
    public static <E> List<E> ordenar(List<E> lista, String orden, Map<String, Campo<E>> campos,
                                      Comparator<E> porDefecto) {
        if (orden == null || orden.isBlank()) return lista;
        String[] partes = orden.split(",", 2);
        Campo<E> campo = campos.get(partes[0].trim());
        if (campo == null) return lista;
        boolean descendente = partes.length > 1 && partes[1].trim().equalsIgnoreCase("desc");
        Comparator<Object> valores = descendente ? campo.comparar().reversed() : campo.comparar();
        Comparator<E> comparador = Comparator.comparing(campo.leer(), Comparator.nullsLast(valores));
        return lista.stream().sorted(comparador.thenComparing(porDefecto)).toList();
    }
}
