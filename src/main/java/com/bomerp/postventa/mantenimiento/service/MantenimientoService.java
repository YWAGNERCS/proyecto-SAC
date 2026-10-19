package com.bomerp.postventa.mantenimiento.service;

import com.bomerp.config.RecursoNoEncontradoException;
import com.bomerp.config.ReglaNegocioException;
import com.bomerp.postventa.mantenimiento.dto.MantenimientoResponseDTO;
import java.time.LocalDate;
import java.util.Map;
import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.postventa.mantenimiento.entity.Mantenimiento;
import com.bomerp.postventa.mantenimiento.repository.MantenimientoRepository;
import com.bomerp.ventas.venta.entity.Venta;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Programa el mantenimiento preventivo posterior a una venta.
 * Fachada pública del módulo: Ventas ya no toca MantenimientoRepository.
 */
@Service
public class MantenimientoService {

    private final MantenimientoRepository mantenimientoRepository;

    public MantenimientoService(MantenimientoRepository mantenimientoRepository) {
        this.mantenimientoRepository = mantenimientoRepository;
    }

    /**
     * RF confirmado con el cliente: si la venta incluye equipos de climatización o
     * refrigeración, se programan 4 mantenimientos (a los 3, 6, 9 y 12 meses).
     */
    @Transactional
    public void programarParaVenta(Venta venta) {
        boolean requiereMantenimiento = venta.getDetalles().stream()
                .anyMatch(d -> d.getProducto().getSector() == Producto.Sector.CLIMATIZACION
                        || d.getProducto().getSector() == Producto.Sector.REFRIGERACION);

        if (requiereMantenimiento) {
            for (int i = 1; i <= 4; i++) {
                Mantenimiento mant = new Mantenimiento();
                mant.setVenta(venta);
                mant.setFechaProgramada(venta.getFechaVenta().plusMonths(3L * i));
                mantenimientoRepository.save(mant);
            }
        }
    }

    /**
     * Agenda de Postventa.
     *  - VENCIDOS: pendientes cuya fecha ya pasó (hay que llamar al cliente ya).
     *  - PROXIMOS: pendientes de hoy a {@code dias} días.
     *  - REALIZADOS: historial de visitas hechas.
     *  - TODOS: sin filtro de estado ni fecha.
     */
    @Transactional(readOnly = true)
    public List<MantenimientoResponseDTO> agenda(String vista, int dias, Long clienteId) {
        LocalDate hoy = LocalDate.now();
        if (dias < 1 || dias > 366) {
            throw new IllegalArgumentException("Los días deben estar entre 1 y 366.");
        }
        List<Mantenimiento> lista = switch (vista == null ? "PROXIMOS" : vista.toUpperCase()) {
            case "VENCIDOS" -> mantenimientoRepository.buscar(
                    Mantenimiento.EstadoMantenimiento.PENDIENTE, null, hoy.minusDays(1), clienteId);
            case "PROXIMOS" -> mantenimientoRepository.buscar(
                    Mantenimiento.EstadoMantenimiento.PENDIENTE, hoy, hoy.plusDays(dias), clienteId);
            case "REALIZADOS" -> mantenimientoRepository.buscar(
                    Mantenimiento.EstadoMantenimiento.REALIZADO, null, null, clienteId).reversed();
            case "TODOS" -> mantenimientoRepository.buscar(null, null, null, clienteId);
            default -> throw new IllegalArgumentException(
                    "Vista no válida: " + vista + " (use VENCIDOS, PROXIMOS, REALIZADOS o TODOS).");
        };
        return lista.stream().map(MantenimientoResponseDTO::desdeEntidad).toList();
    }

    /** Cuántos hay por atender: vencidos y próximos 7 y 30 días. */
    @Transactional(readOnly = true)
    public Map<String, Integer> resumen() {
        LocalDate hoy = LocalDate.now();
        Mantenimiento.EstadoMantenimiento pendiente = Mantenimiento.EstadoMantenimiento.PENDIENTE;
        return Map.of(
                "vencidos", mantenimientoRepository.buscar(pendiente, null, hoy.minusDays(1), null).size(),
                "proximos7", mantenimientoRepository.buscar(pendiente, hoy, hoy.plusDays(7), null).size(),
                "proximos30", mantenimientoRepository.buscar(pendiente, hoy, hoy.plusDays(30), null).size());
    }

    /** La visita se hizo. Fecha por defecto hoy; no puede ser futura. */
    @Transactional
    public MantenimientoResponseDTO marcarRealizado(Long id, LocalDate fecha, String notas) {
        Mantenimiento m = obtenerPendiente(id, "marcar como realizado");
        LocalDate dia = fecha != null ? fecha : LocalDate.now();
        if (dia.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La fecha de la visita no puede ser futura.");
        }
        m.setEstado(Mantenimiento.EstadoMantenimiento.REALIZADO);
        m.setFechaRealizada(dia);
        agregarNota(m, notas);
        return MantenimientoResponseDTO.desdeEntidad(mantenimientoRepository.save(m));
    }

    /** El cliente pidió otra fecha: se mueve y la alerta por correo vuelve a enviarse. */
    @Transactional
    public MantenimientoResponseDTO reprogramar(Long id, LocalDate nuevaFecha, String notas) {
        Mantenimiento m = obtenerPendiente(id, "reprogramar");
        if (nuevaFecha == null || nuevaFecha.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("La nueva fecha es obligatoria y no puede ser anterior a hoy.");
        }
        agregarNota(m, "Reprogramado del " + m.getFechaProgramada() + " al " + nuevaFecha
                + (notas != null && !notas.isBlank() ? ": " + notas.trim() : ""));
        m.setFechaProgramada(nuevaFecha);
        m.setAlertaEnviada(false);
        return MantenimientoResponseDTO.desdeEntidad(mantenimientoRepository.save(m));
    }

    private Mantenimiento obtenerPendiente(Long id, String accion) {
        Mantenimiento m = mantenimientoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mantenimiento no encontrado: " + id));
        if (m.getEstado() != Mantenimiento.EstadoMantenimiento.PENDIENTE) {
            throw new ReglaNegocioException("No se puede " + accion + " el mantenimiento #" + id
                    + ": está " + m.getEstado() + ".");
        }
        return m;
    }

    /** Las notas se acumulan (máx. 500 caracteres en la base: se conservan las más recientes). */
    private static void agregarNota(Mantenimiento m, String nota) {
        if (nota == null || nota.isBlank()) {
            return;
        }
        String linea = LocalDate.now() + ": " + nota.trim();
        String todas = m.getNotas() == null || m.getNotas().isBlank() ? linea : m.getNotas() + "\n" + linea;
        m.setNotas(todas.length() > 500 ? todas.substring(todas.length() - 500) : todas);
    }

    /**
     * Al anular una venta, sus mantenimientos aún PENDIENTES ya no se harán: pasan a CANCELADO.
     * Los REALIZADOS se conservan como historial.
     *
     * @return cuántos mantenimientos se cancelaron
     */
    @Transactional
    public int cancelarPendientesDeVenta(Long ventaId) {
        List<Mantenimiento> pendientes = mantenimientoRepository.findByVenta_IdAndEstado(
                ventaId, Mantenimiento.EstadoMantenimiento.PENDIENTE);
        pendientes.forEach(m -> m.setEstado(Mantenimiento.EstadoMantenimiento.CANCELADO));
        mantenimientoRepository.saveAll(pendientes);
        return pendientes.size();
    }
}
