package com.bomerp.postventa.mantenimiento.service;

import com.bomerp.config.RecursoNoEncontradoException;
import com.bomerp.config.ReglaNegocioException;
import com.bomerp.postventa.mantenimiento.dto.MantenimientoResponseDTO;
import java.time.LocalDate;
import java.util.Map;
import com.bomerp.postventa.contrato.entity.ContratoMantenimiento;
import com.bomerp.postventa.equipo.entity.EquipoInstalado;
import com.bomerp.postventa.mantenimiento.entity.Mantenimiento;
import com.bomerp.postventa.mantenimiento.repository.MantenimientoRepository;
import com.bomerp.ventas.venta.entity.Venta;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Programa y lleva el mantenimiento preventivo: por venta (4 visitas), por equipo instalado
 * (según su frecuencia) y por contrato. Fachada pública del módulo: Ventas, Equipos, Contratos
 * y Órdenes de trabajo no tocan MantenimientoRepository.
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
                .anyMatch(d -> d.getProducto().requiereMantenimiento());

        if (requiereMantenimiento) {
            for (int i = 1; i <= 4; i++) {
                Mantenimiento mant = new Mantenimiento();
                mant.setCliente(venta.getCliente());
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
        Mantenimiento guardado = mantenimientoRepository.save(m);
        extenderPlanDeEquipo(guardado);
        return MantenimientoResponseDTO.desdeEntidad(guardado);
    }

    /**
     * Fachada para Órdenes de trabajo: el mantenimiento debe estar PENDIENTE y ser del cliente de la orden.
     */
    @Transactional(readOnly = true)
    public Mantenimiento obtenerPendienteDeCliente(Long id, Long clienteId) {
        Mantenimiento m = obtenerPendiente(id, "asignar a una orden de trabajo");
        if (!m.getCliente().getId().equals(clienteId)) {
            throw new IllegalArgumentException("El mantenimiento #" + id + " es de otro cliente.");
        }
        return m;
    }

    /** Fachada para Órdenes de trabajo: técnico asignado al programar la orden. */
    @Transactional
    public void asignarTecnico(Long id, String tecnico) {
        mantenimientoRepository.findById(id).ifPresent(m -> {
            m.setTecnico(tecnico);
            mantenimientoRepository.save(m);
        });
    }

    /** Fachada para Órdenes de trabajo: al cerrar la orden, la visita queda REALIZADA con su técnico. */
    @Transactional
    public void realizarPorOrden(Long id, LocalDate fecha, String tecnico, Long ordenTrabajoId) {
        Mantenimiento m = obtenerPendiente(id, "marcar como realizado");
        m.setEstado(Mantenimiento.EstadoMantenimiento.REALIZADO);
        m.setFechaRealizada(fecha);
        m.setTecnico(tecnico);
        m.setOrdenTrabajoId(ordenTrabajoId);
        agregarNota(m, "Realizado con la orden de trabajo #" + ordenTrabajoId);
        extenderPlanDeEquipo(mantenimientoRepository.save(m));
    }

    // ── Plan por equipo instalado ───────────────────────────────

    /** Las visitas se programan con un año de anticipación; al realizar una, el plan se extiende. */
    private static final int HORIZONTE_MESES = 12;

    /**
     * (Re)programa las visitas de un equipo según su frecuencia: cancela las pendientes que tenía
     * y programa desde la última visita realizada (o desde la instalación), solo fechas de hoy en adelante.
     * Sin frecuencia o retirado, solo se cancelan las pendientes.
     *
     * @return cuántas visitas quedaron programadas
     */
    @Transactional
    public int replanificarEquipo(EquipoInstalado equipo, String motivo) {
        cancelar(mantenimientoRepository.findByEquipo_IdAndEstado(equipo.getId(), Mantenimiento.EstadoMantenimiento.PENDIENTE), motivo);
        return programarEquipo(equipo, List.of(Mantenimiento.EstadoMantenimiento.REALIZADO));
    }

    /**
     * Al registrar un equipo con plan propio desde una venta, las 4 visitas genéricas de esa venta
     * se cancelan: las reemplaza el plan del equipo (así no hay visitas duplicadas).
     */
    @Transactional
    public int reemplazarPlanDeVenta(Long ventaId) {
        List<Mantenimiento> genericos = mantenimientoRepository.findByVenta_IdAndEquipoIsNullAndEstado(
                ventaId, Mantenimiento.EstadoMantenimiento.PENDIENTE);
        cancelar(genericos, "Reemplazado por el plan del equipo instalado");
        return genericos.size();
    }

    private void extenderPlanDeEquipo(Mantenimiento hecho) {
        if (hecho.getEquipo() != null) {
            programarEquipo(hecho.getEquipo(),
                    List.of(Mantenimiento.EstadoMantenimiento.REALIZADO, Mantenimiento.EstadoMantenimiento.PENDIENTE));
        }
    }

    private int programarEquipo(EquipoInstalado equipo, List<Mantenimiento.EstadoMantenimiento> estadosBase) {
        if (equipo.getFrecuenciaMeses() == null || equipo.getEstado() != EquipoInstalado.Estado.ACTIVO) {
            return 0;
        }
        LocalDate ultima = mantenimientoRepository.ultimaFechaDeEquipo(equipo.getId(), estadosBase);
        LocalDate hoy = LocalDate.now();
        LocalDate horizonte = hoy.plusMonths(HORIZONTE_MESES);
        LocalDate fecha = (ultima != null ? ultima : equipo.getFechaInstalacion()).plusMonths(equipo.getFrecuenciaMeses());
        int programadas = 0;
        while (!fecha.isAfter(horizonte)) {
            if (!fecha.isBefore(hoy)) {
                Mantenimiento m = new Mantenimiento();
                m.setCliente(equipo.getCliente());
                m.setVenta(equipo.getVenta());
                m.setEquipo(equipo);
                m.setFechaProgramada(fecha);
                mantenimientoRepository.save(m);
                programadas++;
            }
            fecha = fecha.plusMonths(equipo.getFrecuenciaMeses());
        }
        return programadas;
    }

    @Transactional(readOnly = true)
    public LocalDate proximaVisitaDeEquipo(Long equipoId) {
        return mantenimientoRepository.proximaDeEquipo(equipoId);
    }

    // ── Plan por contrato ───────────────────────────────────────

    /**
     * Programa las visitas del contrato: desde su inicio, cada {@code frecuenciaMeses} meses, hasta su fin
     * (solo fechas de hoy en adelante).
     *
     * @return cuántas visitas se programaron
     */
    @Transactional
    public int programarContrato(ContratoMantenimiento contrato) {
        LocalDate hoy = LocalDate.now();
        int programadas = 0;
        for (LocalDate fecha = contrato.getFechaInicio(); !fecha.isAfter(contrato.getFechaFin());
             fecha = fecha.plusMonths(contrato.getFrecuenciaMeses())) {
            if (!fecha.isBefore(hoy)) {
                Mantenimiento m = new Mantenimiento();
                m.setCliente(contrato.getCliente());
                m.setContrato(contrato);
                m.setFechaProgramada(fecha);
                mantenimientoRepository.save(m);
                programadas++;
            }
        }
        return programadas;
    }

    /** Al cancelar un contrato sus visitas pendientes ya no se harán. @return cuántas se cancelaron */
    @Transactional
    public int cancelarPendientesDeContrato(Long contratoId, String motivo) {
        List<Mantenimiento> pendientes = mantenimientoRepository.findByContrato_IdAndEstado(
                contratoId, Mantenimiento.EstadoMantenimiento.PENDIENTE);
        cancelar(pendientes, motivo);
        return pendientes.size();
    }

    @Transactional(readOnly = true)
    public long contarDeContrato(Long contratoId, Mantenimiento.EstadoMantenimiento estado) {
        return mantenimientoRepository.countByContrato_IdAndEstado(contratoId, estado);
    }

    private void cancelar(List<Mantenimiento> lista, String motivo) {
        lista.forEach(m -> {
            m.setEstado(Mantenimiento.EstadoMantenimiento.CANCELADO);
            agregarNota(m, motivo);
        });
        mantenimientoRepository.saveAll(lista);
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
