package com.bomerp.postventa.mantenimiento.dto;

import com.bomerp.clientes.general.integration.Cliente;
import com.bomerp.clientes.sede.entity.SedeCliente;
import com.bomerp.postventa.contrato.entity.ContratoMantenimiento;
import com.bomerp.postventa.equipo.entity.EquipoInstalado;
import com.bomerp.postventa.mantenimiento.entity.Mantenimiento;
import com.bomerp.ventas.venta.entity.Venta;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;

/**
 * Un mantenimiento con lo necesario para llamar al cliente: contacto, equipos, sede y cuántos días
 * faltan (negativo = ya pasó la fecha). {@code origen} dice de dónde salió: VENTA, EQUIPO o CONTRATO.
 */
public record MantenimientoResponseDTO(
        Long id,
        String origen,
        Long ventaId,
        LocalDate fechaVenta,
        Long equipoId,
        Long contratoId,
        Long clienteId,
        String clienteNombre,
        String clienteTelefono,
        String clienteCorreo,
        String clienteDireccion,
        String sedeNombre,
        String equipos,
        LocalDate fechaProgramada,
        long diasParaFecha,
        String estado,
        LocalDate fechaRealizada,
        String tecnico,
        Long ordenTrabajoId,
        String notas
) {
    public static MantenimientoResponseDTO desdeEntidad(Mantenimiento m) {
        Cliente c = m.getCliente();
        Venta v = m.getVenta();
        EquipoInstalado e = m.getEquipo();
        ContratoMantenimiento k = m.getContrato();
        SedeCliente sede = e != null ? e.getSede() : k != null ? k.getSede() : null;

        String origen;
        String equipos;
        if (e != null) {
            origen = "EQUIPO";
            equipos = e.getProducto().getNombre()
                    + (e.getNumeroSerie() != null ? " (serie " + e.getNumeroSerie() + ")" : "")
                    + (e.getUbicacion() != null ? " · " + e.getUbicacion() : "");
        } else if (k != null) {
            origen = "CONTRATO";
            equipos = "Contrato #" + k.getId() + ": " + k.getDescripcion();
        } else {
            origen = "VENTA";
            equipos = v.getDetalles().stream()
                    .filter(d -> d.getProducto().requiereMantenimiento())
                    .map(d -> d.getCantidad() + " × " + d.getProducto().getNombre())
                    .collect(Collectors.joining(", "));
        }
        return new MantenimientoResponseDTO(
                m.getId(),
                origen,
                v != null ? v.getId() : null,
                v != null ? v.getFechaVenta() : null,
                e != null ? e.getId() : null,
                k != null ? k.getId() : null,
                c.getId(),
                c.getNombreORazonSocial(),
                sede != null && sede.getTelefono() != null ? sede.getTelefono() : c.getTelefono(),
                c.getCorreo(),
                sede != null && sede.getDireccion() != null ? sede.getDireccion() : c.getDireccion(),
                sede != null ? sede.getNombre() : null,
                equipos,
                m.getFechaProgramada(),
                ChronoUnit.DAYS.between(LocalDate.now(), m.getFechaProgramada()),
                m.getEstado().name(),
                m.getFechaRealizada(),
                m.getTecnico(),
                m.getOrdenTrabajoId(),
                m.getNotas());
    }
}
