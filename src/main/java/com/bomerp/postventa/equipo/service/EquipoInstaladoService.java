package com.bomerp.postventa.equipo.service;

import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.catalogo.producto.service.ProductoService;
import com.bomerp.clientes.cliente.service.ClienteService;
import com.bomerp.clientes.general.integration.Cliente;
import com.bomerp.clientes.sede.service.SedeClienteService;
import com.bomerp.config.RecursoNoEncontradoException;
import com.bomerp.config.ReglaNegocioException;
import com.bomerp.postventa.equipo.dto.EquipoDTO;
import com.bomerp.postventa.equipo.entity.EquipoInstalado;
import com.bomerp.postventa.equipo.repository.EquipoInstaladoRepository;
import com.bomerp.postventa.mantenimiento.service.MantenimientoService;
import com.bomerp.ventas.venta.entity.Venta;
import com.bomerp.ventas.venta.service.VentaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Parque instalado: equipos de JD en los locales de sus clientes, con garantía y plan de mantenimiento.
 *
 * Reglas: solo equipos del catálogo (climatización, refrigeración, ventilación); la sede y la venta
 * (si se indican) son del mismo cliente; el mismo número de serie no se repite para el mismo modelo;
 * la fecha de instalación no es futura; la frecuencia de mantenimiento es de 1, 2, 3, 4, 6 o 12 meses.
 */
@Service
@Transactional
@Slf4j
public class EquipoInstaladoService {

    public static final Set<Integer> FRECUENCIAS = Set.of(1, 2, 3, 4, 6, 12);

    private final EquipoInstaladoRepository equipoRepository;
    private final ClienteService clienteService;
    private final SedeClienteService sedeService;
    private final ProductoService productoService;
    private final VentaService ventaService;
    private final MantenimientoService mantenimientoService;

    public EquipoInstaladoService(EquipoInstaladoRepository equipoRepository, ClienteService clienteService,
                                  SedeClienteService sedeService, ProductoService productoService,
                                  VentaService ventaService, MantenimientoService mantenimientoService) {
        this.equipoRepository = equipoRepository;
        this.clienteService = clienteService;
        this.sedeService = sedeService;
        this.productoService = productoService;
        this.ventaService = ventaService;
        this.mantenimientoService = mantenimientoService;
    }

    @Transactional(readOnly = true)
    public List<EquipoDTO.Response> listar(Long clienteId, Long sedeId, EquipoInstalado.Estado estado, Boolean enGarantia) {
        return equipoRepository.buscar(clienteId, sedeId, estado).stream()
                .filter(e -> enGarantia == null || e.enGarantia() == enGarantia)
                .map(this::aDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public EquipoDTO.Response obtener(Long id) {
        return aDto(buscar(id));
    }

    @Transactional(readOnly = true)
    public List<EquipoDTO.Response> deOrden(Long ordenTrabajoId) {
        return equipoRepository.findByOrdenTrabajoId(ordenTrabajoId).stream().map(this::aDto).toList();
    }

    /** Registro a mano (equipos instalados antes del sistema o por otro proveedor). */
    public EquipoDTO.Response registrar(EquipoDTO.Registro dto) {
        return aDto(registrarEquipo(dto, null));
    }

    /**
     * Fachada para Órdenes de trabajo: registra un equipo instalado por una orden de instalación.
     * Si tiene frecuencia de mantenimiento, se programa su plan; si además viene de una venta,
     * las 4 visitas genéricas de esa venta se cancelan (las reemplaza el plan del equipo).
     */
    public EquipoInstalado registrarEquipo(EquipoDTO.Registro dto, Long ordenTrabajoId) {
        Cliente cliente = clienteService.obtenerPorId(dto.clienteId());
        Producto producto = productoService.obtenerPorId(dto.productoId());
        if (!producto.esEquipo()) {
            throw new IllegalArgumentException("'" + producto.getNombre() + "' no es un equipo (es "
                    + producto.getSector() + "): solo se registran equipos de climatización, refrigeración o ventilación.");
        }
        if (dto.fechaInstalacion().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La fecha de instalación no puede ser futura.");
        }
        validarFrecuencia(dto.frecuenciaMeses());
        String serie = vacioANulo(dto.numeroSerie());
        if (serie != null && equipoRepository.existsByProducto_IdAndNumeroSerieIgnoreCase(producto.getId(), serie)) {
            throw new ReglaNegocioException("Ya hay un " + producto.getNombre() + " registrado con la serie " + serie + ".");
        }

        EquipoInstalado e = new EquipoInstalado();
        e.setCliente(cliente);
        e.setSede(dto.sedeId() != null ? sedeService.obtenerDeCliente(cliente.getId(), dto.sedeId()) : null);
        e.setProducto(producto);
        e.setNumeroSerie(serie);
        e.setUbicacion(vacioANulo(dto.ubicacion()));
        e.setFechaInstalacion(dto.fechaInstalacion());
        e.setMesesGarantia(dto.mesesGarantia() != null ? dto.mesesGarantia() : 12);
        e.setFrecuenciaMeses(dto.frecuenciaMeses());
        e.setNotas(vacioANulo(dto.notas()));
        e.setOrdenTrabajoId(ordenTrabajoId);
        if (dto.ventaId() != null) {
            Venta venta = ventaService.obtenerPorIdPublico(dto.ventaId());
            if (!venta.getCliente().getId().equals(cliente.getId())) {
                throw new IllegalArgumentException("La venta #" + venta.getId() + " es de otro cliente.");
            }
            if (venta.getEstado() == Venta.EstadoVenta.ANULADA) {
                throw new ReglaNegocioException("La venta #" + venta.getId() + " está ANULADA.");
            }
            e.setVenta(venta);
        }
        EquipoInstalado guardado = equipoRepository.save(e);

        if (guardado.getFrecuenciaMeses() != null) {
            if (guardado.getVenta() != null) {
                mantenimientoService.reemplazarPlanDeVenta(guardado.getVenta().getId());
            }
            int visitas = mantenimientoService.replanificarEquipo(guardado, "Plan inicial");
            log.info("Equipo {} registrado ({}): {} visita(s) programada(s) cada {} mes(es)",
                    guardado.getId(), producto.getNombre(), visitas, guardado.getFrecuenciaMeses());
        }
        return guardado;
    }

    /** Cambia sede, serie, ubicación, garantía o plan. Si cambia la frecuencia, se reprograman las visitas pendientes. */
    public EquipoDTO.Response actualizar(Long id, EquipoDTO.Edicion dto) {
        EquipoInstalado e = buscar(id);
        if (e.getEstado() == EquipoInstalado.Estado.RETIRADO) {
            throw new ReglaNegocioException("El equipo #" + id + " está RETIRADO: ya no se edita.");
        }
        validarFrecuencia(dto.frecuenciaMeses());
        String serie = vacioANulo(dto.numeroSerie());
        if (serie != null && equipoRepository.existsByProducto_IdAndNumeroSerieIgnoreCaseAndIdNot(e.getProducto().getId(), serie, id)) {
            throw new ReglaNegocioException("Ya hay otro " + e.getProducto().getNombre() + " con la serie " + serie + ".");
        }
        boolean cambiaPlan = !Objects.equals(e.getFrecuenciaMeses(), dto.frecuenciaMeses());
        e.setSede(dto.sedeId() != null ? sedeService.obtenerDeCliente(e.getCliente().getId(), dto.sedeId()) : null);
        e.setNumeroSerie(serie);
        e.setUbicacion(vacioANulo(dto.ubicacion()));
        e.setMesesGarantia(dto.mesesGarantia());
        e.setFrecuenciaMeses(dto.frecuenciaMeses());
        e.setNotas(vacioANulo(dto.notas()));
        EquipoInstalado guardado = equipoRepository.save(e);
        if (cambiaPlan) {
            mantenimientoService.replanificarEquipo(guardado, dto.frecuenciaMeses() == null
                    ? "Se quitó el plan de mantenimiento del equipo"
                    : "Nueva frecuencia: cada " + dto.frecuenciaMeses() + " mes(es)");
        }
        return aDto(guardado);
    }

    /** El equipo se desinstaló o se dio de baja: queda en el historial y sus visitas pendientes se cancelan. */
    public EquipoDTO.Response retirar(Long id, String motivo) {
        EquipoInstalado e = buscar(id);
        if (e.getEstado() == EquipoInstalado.Estado.RETIRADO) {
            throw new ReglaNegocioException("El equipo #" + id + " ya está RETIRADO.");
        }
        e.setEstado(EquipoInstalado.Estado.RETIRADO);
        String nota = LocalDate.now() + ": retirado — " + motivo.trim();
        e.setNotas(e.getNotas() == null ? nota : (e.getNotas() + " · " + nota));
        if (e.getNotas().length() > 300) {
            e.setNotas(e.getNotas().substring(e.getNotas().length() - 300));
        }
        EquipoInstalado guardado = equipoRepository.save(e);
        mantenimientoService.replanificarEquipo(guardado, "Equipo retirado: " + motivo.trim());
        return aDto(guardado);
    }

    /** Fachada: el equipo debe existir y ser de ese cliente. */
    @Transactional(readOnly = true)
    public EquipoInstalado obtenerDeCliente(Long id, Long clienteId) {
        EquipoInstalado e = buscar(id);
        if (!e.getCliente().getId().equals(clienteId)) {
            throw new IllegalArgumentException("El equipo #" + id + " es de otro cliente.");
        }
        return e;
    }

    private EquipoInstalado buscar(Long id) {
        return equipoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Equipo instalado no encontrado: " + id));
    }

    private EquipoDTO.Response aDto(EquipoInstalado e) {
        return EquipoDTO.Response.desdeEntidad(e, mantenimientoService.proximaVisitaDeEquipo(e.getId()));
    }

    private static void validarFrecuencia(Integer frecuencia) {
        if (frecuencia != null && !FRECUENCIAS.contains(frecuencia)) {
            throw new IllegalArgumentException("La frecuencia de mantenimiento debe ser de 1, 2, 3, 4, 6 o 12 meses.");
        }
    }

    private static String vacioANulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
