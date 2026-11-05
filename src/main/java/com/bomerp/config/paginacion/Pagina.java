package com.bomerp.config.paginacion;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.function.Function;

/**
 * Una página de un listado: lo que se ve y cuánto hay en total. Las pantallas de Ventas, Proformas y
 * Compras piden de a 20 (o 50 / 100) en vez de traer todo: viaja y se dibuja menos.
 *
 * @param pagina número de página, desde 0
 * @param total  cantidad de registros que cumplen el filtro (en todas las páginas)
 */
public record Pagina<T>(List<T> contenido, int pagina, int tamano, long total, int totalPaginas) {

    public static final int TAMANO_POR_DEFECTO = 20;
    public static final int TAMANO_MAXIMO = 100;

    /** Página y tamaño válidos: página desde 0 y tamaño entre 1 y 100 (20 si no se indica). */
    public static Pageable pedido(Integer pagina, Integer tamano) {
        int p = pagina == null || pagina < 0 ? 0 : pagina;
        int t = tamano == null || tamano < 1 ? TAMANO_POR_DEFECTO : Math.min(tamano, TAMANO_MAXIMO);
        return PageRequest.of(p, t);
    }

    /** Desde una página de Spring Data (la base ya paginó), convirtiendo cada registro. */
    public static <E, T> Pagina<T> de(Page<E> page, Function<E, T> convertir) {
        return new Pagina<>(page.getContent().stream().map(convertir).toList(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    /** Desde una lista ya filtrada (cuando además hay que resumir todo el filtro): convierte solo la página. */
    public static <E, T> Pagina<T> deLista(List<E> todos, Pageable pedido, Function<E, T> convertir) {
        int desde = (int) Math.min(pedido.getOffset(), todos.size());
        int hasta = Math.min(desde + pedido.getPageSize(), todos.size());
        int totalPaginas = (int) Math.ceil(todos.size() / (double) pedido.getPageSize());
        return new Pagina<>(todos.subList(desde, hasta).stream().map(convertir).toList(), pedido.getPageNumber(),
                pedido.getPageSize(), todos.size(), totalPaginas);
    }
}
