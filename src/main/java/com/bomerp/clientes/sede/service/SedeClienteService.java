package com.bomerp.clientes.sede.service;

import com.bomerp.clientes.cliente.service.ClienteService;
import com.bomerp.clientes.general.integration.Cliente;
import com.bomerp.clientes.sede.dto.SedeDTO;
import com.bomerp.clientes.sede.entity.SedeCliente;
import com.bomerp.clientes.sede.repository.SedeClienteRepository;
import com.bomerp.config.RecursoNoEncontradoException;
import com.bomerp.config.ReglaNegocioException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Sedes (locales) de un cliente. Fachada pública: otros módulos usan obtenerDeCliente. */
@Service
@Transactional
public class SedeClienteService {

    private final SedeClienteRepository sedeRepository;
    private final ClienteService clienteService;

    public SedeClienteService(SedeClienteRepository sedeRepository, ClienteService clienteService) {
        this.sedeRepository = sedeRepository;
        this.clienteService = clienteService;
    }

    @Transactional(readOnly = true)
    public List<SedeDTO.Response> listar(Long clienteId) {
        clienteService.obtenerPorId(clienteId);   // 404 si no existe
        return sedeRepository.findByCliente_IdOrderByNombre(clienteId).stream().map(SedeDTO.Response::desdeEntidad).toList();
    }

    public SedeDTO.Response crear(Long clienteId, SedeDTO.Request dto) {
        Cliente cliente = clienteService.obtenerPorId(clienteId);
        String nombre = dto.nombre().trim();
        if (sedeRepository.existsByCliente_IdAndNombreIgnoreCase(clienteId, nombre)) {
            throw new ReglaNegocioException("El cliente ya tiene una sede llamada '" + nombre + "'.");
        }
        SedeCliente sede = new SedeCliente();
        sede.setCliente(cliente);
        copiar(dto, sede);
        return SedeDTO.Response.desdeEntidad(sedeRepository.save(sede));
    }

    public SedeDTO.Response actualizar(Long clienteId, Long sedeId, SedeDTO.Request dto) {
        SedeCliente sede = obtenerDeCliente(clienteId, sedeId);
        if (sedeRepository.existsByCliente_IdAndNombreIgnoreCaseAndIdNot(clienteId, dto.nombre().trim(), sedeId)) {
            throw new ReglaNegocioException("El cliente ya tiene otra sede llamada '" + dto.nombre().trim() + "'.");
        }
        copiar(dto, sede);
        return SedeDTO.Response.desdeEntidad(sedeRepository.save(sede));
    }

    /** 409 si la sede ya tiene equipos, órdenes de trabajo o contratos: se conserva el historial. */
    public void eliminar(Long clienteId, Long sedeId) {
        SedeCliente sede = obtenerDeCliente(clienteId, sedeId);
        try {
            sedeRepository.delete(sede);
            sedeRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ReglaNegocioException("No se puede eliminar la sede '" + sede.getNombre()
                    + "': tiene equipos, órdenes de trabajo o contratos registrados.");
        }
    }

    /** Fachada: la sede debe existir y ser de ese cliente (404 / 400). */
    @Transactional(readOnly = true)
    public SedeCliente obtenerDeCliente(Long clienteId, Long sedeId) {
        SedeCliente sede = sedeRepository.findById(sedeId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Sede no encontrada: " + sedeId));
        if (!sede.getCliente().getId().equals(clienteId)) {
            throw new IllegalArgumentException("La sede '" + sede.getNombre() + "' no es de ese cliente.");
        }
        return sede;
    }

    private static void copiar(SedeDTO.Request dto, SedeCliente sede) {
        sede.setNombre(dto.nombre().trim());
        sede.setDireccion(vacioANulo(dto.direccion()));
        sede.setContacto(vacioANulo(dto.contacto()));
        sede.setTelefono(vacioANulo(dto.telefono()));
    }

    private static String vacioANulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
