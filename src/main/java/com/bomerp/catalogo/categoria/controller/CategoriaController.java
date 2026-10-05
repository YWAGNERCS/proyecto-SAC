package com.bomerp.catalogo.categoria.controller;

import com.bomerp.catalogo.categoria.entity.Categoria;
import com.bomerp.catalogo.categoria.service.CategoriaService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categorias")
@Slf4j
public class CategoriaController {

    private final CategoriaService categoriaService;

    public CategoriaController(CategoriaService categoriaService) {
        this.categoriaService = categoriaService;
    }

    @GetMapping
    public List<Categoria> listar(@RequestHeader(value = "X-Trace-ID", required = false) String traceId) {
        log.info("[TraceID: {}] Listando todas las categorías", traceId);
        return categoriaService.listarTodas();
    }

    @GetMapping("/{id}")
    public Categoria obtener(
            @PathVariable Long id,
            @RequestHeader(value = "X-Trace-ID", required = false) String traceId) {
        log.info("[TraceID: {}] Obteniendo categoría con ID: {}", traceId, id);
        return categoriaService.obtenerPorId(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Categoria crear(
            @Valid @RequestBody Categoria categoria,
            @RequestHeader(value = "X-Trace-ID", required = false) String traceId) {
        log.info("[TraceID: {}] Creando nueva categoría: {}", traceId, categoria.getNombre());
        return categoriaService.crear(categoria);
    }

    @PutMapping("/{id}")
    public Categoria actualizar(
            @PathVariable Long id,
            @Valid @RequestBody Categoria categoria,
            @RequestHeader(value = "X-Trace-ID", required = false) String traceId) {
        log.info("[TraceID: {}] Actualizando categoría con ID: {}", traceId, id);
        return categoriaService.actualizar(id, categoria);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(
            @PathVariable Long id,
            @RequestHeader(value = "X-Trace-ID", required = false) String traceId) {
        log.info("[TraceID: {}] Eliminando categoría con ID: {}", traceId, id);
        categoriaService.eliminar(id);
    }
}
