package com.bomerp.postventa.mantenimiento.dto;

import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.postventa.mantenimiento.entity.Mantenimiento;
import com.bomerp.ventas.venta.entity.Venta;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;

/**
 * Un mantenimiento con lo necesario para llamar al cliente: contacto, equipos instalados
 * y cuántos días faltan (negativo = ya pasó la fecha).
 */
public record MantenimientoResponseDTO(
        Long id,
        Long ventaId,
        LocalDate fechaVenta,
        Long clienteId,
        String clienteNombre,
        String clienteTelefono,
        String clienteCorreo,
        String clienteDireccion,
        String equipos,
        LocalDate fechaProgramada,
        long diasParaFecha,
        String estado,
        LocalDate fechaRealizada,
        String notas
) {
    public static MantenimientoResponseDTO desdeEntidad(Mantenimiento m) {
        Venta v = m.getVenta();
        String equipos = v.getDetalles().stream()
                .filter(d -> d.getProducto().getSector() == Producto.Sector.CLIMATIZACION
                        || d.getProducto().getSector() == Producto.Sector.REFRIGERACION)
                .map(d -> d.getCantidad() + " × " + d.getProducto().getNombre())
                .collect(Collectors.joining(", "));
        return new MantenimientoResponseDTO(
                m.getId(),
                v.getId(),
                v.getFechaVenta(),
                v.getCliente().getId(),
                v.getCliente().getNombreORazonSocial(),
                v.getCliente().getTelefono(),
                v.getCliente().getCorreo(),
                v.getCliente().getDireccion(),
                equipos,
                m.getFechaProgramada(),
                ChronoUnit.DAYS.between(LocalDate.now(), m.getFechaProgramada()),
                m.getEstado().name(),
                m.getFechaRealizada(),
                m.getNotas());
    }
}
