package com.bomerp.inventario.ajuste.service;

import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.catalogo.producto.service.ProductoService;
import com.bomerp.config.ReglaNegocioException;
import com.bomerp.inventario.ajuste.entity.AjusteInventario;
import com.bomerp.inventario.ajuste.repository.AjusteInventarioRepository;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Ajustes de inventario (solo materiales: los equipos no controlan stock).
 *
 * Dos formas de registrar:
 *  - Conteo físico: se escribe cuántas unidades hay de verdad (stockContado) y el sistema calcula
 *    la diferencia contra el stock del sistema (entrada si sobra, salida si falta).
 *  - Movimiento: tipo (ENTRADA/SALIDA) y cantidad, con su motivo (merma, dañado, robo...).
 * Una salida no puede dejar el stock negativo. Toda salida o entrada fuera del conteo pide una
 * observación que explique el ajuste.
 */
@Service
@Transactional
@Slf4j
public class AjusteInventarioService {

    private final AjusteInventarioRepository repository;
    private final ProductoService productoService;

    public AjusteInventarioService(AjusteInventarioRepository repository, ProductoService productoService) {
        this.repository = repository;
        this.productoService = productoService;
    }

    public record AjusteRequest(
            @NotNull(message = "Indique el producto") Long productoId,
            @NotNull(message = "Indique el motivo") AjusteInventario.Motivo motivo,
            LocalDate fecha,                              // opcional: hoy
            /** Conteo físico: unidades contadas. Si viene, tipo y cantidad se calculan. */
            @Min(value = 0, message = "El stock contado no puede ser negativo") Integer stockContado,
            AjusteInventario.Tipo tipo,
            @Min(value = 1, message = "La cantidad debe ser al menos 1") Integer cantidad,
            @Size(max = 300) String observacion
    ) {}

    public record AjusteDTO(Long id, Long productoId, String producto, LocalDate fecha, String tipo, int cantidad,
                            String motivo, String motivoNombre, String observacion, int stockAnterior, int stockNuevo,
                            String registradoPor, LocalDateTime fechaRegistro) {
        static AjusteDTO de(AjusteInventario a) {
            return new AjusteDTO(a.getId(), a.getProducto().getId(), a.getProducto().getNombre(), a.getFecha(),
                    a.getTipo().name(), a.getCantidad(), a.getMotivo().name(), a.getMotivo().getNombre(),
                    a.getObservacion(), a.getStockAnterior(), a.getStockNuevo(), a.getRegistradoPor(), a.getFechaRegistro());
        }
    }

    public record MotivoDTO(String codigo, String nombre) {}

    @Transactional(readOnly = true)
    public List<AjusteDTO> buscar(Long productoId, AjusteInventario.Motivo motivo, LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new IllegalArgumentException("La fecha 'desde' no puede ser posterior a 'hasta'.");
        }
        return repository.buscar(productoId, motivo, desde, hasta).stream().map(AjusteDTO::de).toList();
    }

    public List<MotivoDTO> motivos() {
        return Arrays.stream(AjusteInventario.Motivo.values()).map(m -> new MotivoDTO(m.name(), m.getNombre())).toList();
    }

    /** Fachada para el kardex. */
    @Transactional(readOnly = true)
    public List<AjusteInventario> deProducto(Long productoId) {
        return repository.deProducto(productoId);
    }

    /** @param usuario preferred_username de quien ajusta */
    public AjusteDTO registrar(AjusteRequest dto, String usuario) {
        Producto producto = productoService.obtenerPorId(dto.productoId());
        if (!producto.controlaStock()) {
            throw new ReglaNegocioException("'" + producto.getNombre() + "' no controla stock (equipo o servicio): no se ajusta. "
                    + "Un equipo dañado o devuelto se registra en Equipos instalados o con el proveedor.");
        }
        LocalDate fecha = dto.fecha() != null ? dto.fecha() : LocalDate.now();
        if (fecha.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La fecha del ajuste no puede ser futura.");
        }
        String observacion = dto.observacion() == null || dto.observacion().isBlank() ? null : dto.observacion().trim();
        int actual = producto.getStockActual() == null ? 0 : producto.getStockActual();

        AjusteInventario.Tipo tipo;
        int cantidad;
        if (dto.stockContado() != null) {
            int diferencia = dto.stockContado() - actual;
            if (diferencia == 0) {
                throw new ReglaNegocioException("El conteo coincide con el sistema (" + actual + "): no hay nada que ajustar.");
            }
            tipo = diferencia > 0 ? AjusteInventario.Tipo.ENTRADA : AjusteInventario.Tipo.SALIDA;
            cantidad = Math.abs(diferencia);
        } else {
            if (dto.tipo() == null || dto.cantidad() == null) {
                throw new IllegalArgumentException("Indique el stock contado, o el tipo (entrada o salida) y la cantidad.");
            }
            if (dto.motivo() == AjusteInventario.Motivo.CONTEO_FISICO) {
                throw new IllegalArgumentException("Para un conteo físico escriba cuántas unidades contó (stockContado).");
            }
            if (observacion == null) {
                throw new IllegalArgumentException("Explique el ajuste en la observación (qué pasó, dónde, quién lo reportó).");
            }
            tipo = dto.tipo();
            cantidad = dto.cantidad();
        }
        int nuevo = tipo == AjusteInventario.Tipo.ENTRADA ? actual + cantidad : actual - cantidad;
        if (nuevo < 0) {
            throw new ReglaNegocioException("La salida de " + cantidad + " dejaría el stock de '" + producto.getNombre()
                    + "' en negativo (hay " + actual + ").");
        }

        AjusteInventario a = new AjusteInventario();
        a.setProducto(producto);
        a.setFecha(fecha);
        a.setTipo(tipo);
        a.setCantidad(cantidad);
        a.setMotivo(dto.motivo());
        a.setObservacion(observacion);
        a.setStockAnterior(actual);
        a.setStockNuevo(nuevo);
        a.setRegistradoPor(usuario);
        a.setFechaRegistro(LocalDateTime.now());
        AjusteInventario guardado = repository.save(a);

        producto.setStockActual(nuevo);
        productoService.guardar(producto);
        log.info("Ajuste {} de '{}' por {}: {} {} ({}), stock {} → {}", guardado.getId(), producto.getNombre(), usuario,
                tipo, cantidad, dto.motivo(), actual, nuevo);
        return AjusteDTO.de(guardado);
    }
}
