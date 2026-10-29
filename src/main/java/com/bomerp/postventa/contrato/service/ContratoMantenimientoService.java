package com.bomerp.postventa.contrato.service;

import com.bomerp.clientes.cliente.service.ClienteService;
import com.bomerp.clientes.general.integration.Cliente;
import com.bomerp.clientes.sede.service.SedeClienteService;
import com.bomerp.config.RecursoNoEncontradoException;
import com.bomerp.config.ReglaNegocioException;
import com.bomerp.postventa.contrato.dto.ContratoDTO;
import com.bomerp.postventa.contrato.entity.ContratoMantenimiento;
import com.bomerp.postventa.contrato.repository.ContratoMantenimientoRepository;
import com.bomerp.postventa.equipo.service.EquipoInstaladoService;
import com.bomerp.postventa.mantenimiento.entity.Mantenimiento;
import com.bomerp.postventa.mantenimiento.service.MantenimientoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.util.List;

/**
 * Contratos de mantenimiento: al crearse programan sus visitas (cada {@code frecuenciaMeses} meses entre
 * sus fechas); cada visita se ejecuta con una orden de trabajo y se factura a {@code montoVisita}.
 *
 * Reglas: la sede es del cliente; la fecha de fin es posterior al inicio y el contrato dura hasta 5 años;
 * la frecuencia es de 1, 2, 3, 4, 6 o 12 meses. Crear y cancelar contratos es solo del ADMIN (SecurityConfig).
 */
@Service
@Transactional
@Slf4j
public class ContratoMantenimientoService {

    private final ContratoMantenimientoRepository contratoRepository;
    private final ClienteService clienteService;
    private final SedeClienteService sedeService;
    private final MantenimientoService mantenimientoService;

    public ContratoMantenimientoService(ContratoMantenimientoRepository contratoRepository, ClienteService clienteService,
                                        SedeClienteService sedeService, MantenimientoService mantenimientoService) {
        this.contratoRepository = contratoRepository;
        this.clienteService = clienteService;
        this.sedeService = sedeService;
        this.mantenimientoService = mantenimientoService;
    }

    @Transactional(readOnly = true)
    public List<ContratoDTO.Response> listar(Long clienteId, ContratoMantenimiento.Estado estado) {
        return contratoRepository.buscar(clienteId, estado).stream().map(this::aDto).toList();
    }

    @Transactional(readOnly = true)
    public ContratoDTO.Response obtener(Long id) {
        return aDto(buscar(id));
    }

    /** @param usuario preferred_username del token de quien lo registra */
    public ContratoDTO.Response crear(ContratoDTO.Request dto, String usuario) {
        Cliente cliente = clienteService.obtenerPorId(dto.clienteId());
        if (!EquipoInstaladoService.FRECUENCIAS.contains(dto.frecuenciaMeses())) {
            throw new IllegalArgumentException("La frecuencia de las visitas debe ser de 1, 2, 3, 4, 6 o 12 meses.");
        }
        if (!dto.fechaFin().isAfter(dto.fechaInicio())) {
            throw new IllegalArgumentException("La fecha de fin debe ser posterior a la de inicio.");
        }
        if (dto.fechaFin().isAfter(dto.fechaInicio().plusYears(5))) {
            throw new IllegalArgumentException("Un contrato dura como máximo 5 años.");
        }

        ContratoMantenimiento k = new ContratoMantenimiento();
        k.setCliente(cliente);
        k.setSede(dto.sedeId() != null ? sedeService.obtenerDeCliente(cliente.getId(), dto.sedeId()) : null);
        k.setFechaInicio(dto.fechaInicio());
        k.setFechaFin(dto.fechaFin());
        k.setFrecuenciaMeses(dto.frecuenciaMeses());
        k.setMontoVisita(dto.montoVisita().setScale(2, RoundingMode.HALF_UP));
        k.setMoneda(dto.moneda());
        k.setDescripcion(dto.descripcion().trim());
        k.setRegistradoPor(usuario);
        ContratoMantenimiento guardado = contratoRepository.save(k);

        int visitas = mantenimientoService.programarContrato(guardado);
        if (visitas == 0) {
            throw new IllegalArgumentException("El contrato no tiene ninguna visita de hoy en adelante: revise las fechas.");
        }
        log.info("Contrato {} de {}: {} visita(s) programada(s) cada {} mes(es)",
                guardado.getId(), cliente.getNombreORazonSocial(), visitas, guardado.getFrecuenciaMeses());
        return aDto(guardado);
    }

    /** Cancela el contrato: sus visitas pendientes ya no se harán (las realizadas quedan en el historial). */
    public ContratoDTO.Response cancelar(Long id, String motivo) {
        ContratoMantenimiento k = buscar(id);
        if (k.getEstado() == ContratoMantenimiento.Estado.CANCELADO) {
            throw new ReglaNegocioException("El contrato #" + id + " ya está CANCELADO.");
        }
        k.setEstado(ContratoMantenimiento.Estado.CANCELADO);
        k.setMotivoCancelacion(motivo.trim());
        ContratoMantenimiento guardado = contratoRepository.save(k);
        mantenimientoService.cancelarPendientesDeContrato(id, "Contrato cancelado: " + motivo.trim());
        return aDto(guardado);
    }

    private ContratoMantenimiento buscar(Long id) {
        return contratoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Contrato no encontrado: " + id));
    }

    private ContratoDTO.Response aDto(ContratoMantenimiento k) {
        return ContratoDTO.Response.desdeEntidad(k,
                mantenimientoService.contarDeContrato(k.getId(), Mantenimiento.EstadoMantenimiento.PENDIENTE),
                mantenimientoService.contarDeContrato(k.getId(), Mantenimiento.EstadoMantenimiento.REALIZADO));
    }
}
