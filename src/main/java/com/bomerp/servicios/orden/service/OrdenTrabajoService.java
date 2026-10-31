package com.bomerp.servicios.orden.service;

import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.catalogo.producto.service.ProductoService;
import com.bomerp.clientes.cliente.service.ClienteService;
import com.bomerp.clientes.general.integration.Cliente;
import com.bomerp.clientes.sede.entity.SedeCliente;
import com.bomerp.clientes.sede.service.SedeClienteService;
import com.bomerp.config.RecursoNoEncontradoException;
import com.bomerp.config.ReglaNegocioException;
import com.bomerp.config.StockInsuficienteException;
import com.bomerp.postventa.equipo.dto.EquipoDTO;
import com.bomerp.postventa.equipo.entity.EquipoInstalado;
import com.bomerp.postventa.equipo.service.EquipoInstaladoService;
import com.bomerp.postventa.mantenimiento.entity.Mantenimiento;
import com.bomerp.postventa.mantenimiento.service.MantenimientoService;
import com.bomerp.servicios.orden.dto.OrdenDTO;
import com.bomerp.servicios.orden.entity.MaterialOrden;
import com.bomerp.servicios.orden.entity.OrdenTrabajo;
import com.bomerp.servicios.orden.repository.OrdenTrabajoRepository;
import com.bomerp.ventas.venta.dto.VentaResponseDTO;
import com.bomerp.ventas.venta.entity.Venta;
import com.bomerp.ventas.venta.service.VentaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Órdenes de trabajo: PROGRAMADA → REALIZADA (al cerrarla) o CANCELADA.
 *
 * Reglas:
 *  - La sede, la venta, la visita de mantenimiento y el equipo (si se indican) son del mismo cliente.
 *  - Una orden con visita de mantenimiento es de tipo MANTENIMIENTO, y esa visita está PENDIENTE y
 *    no tiene otra orden programada.
 *  - Solo se edita, cierra o cancela una orden PROGRAMADA.
 *  - Al cerrar: fecha no futura; los materiales son productos con stock (MATERIAL_SERVICIO), hay stock
 *    suficiente y se descuenta; los equipos instalados solo en órdenes de INSTALACION; la visita de
 *    mantenimiento queda REALIZADA.
 *  - Solo se factura una orden REALIZADA, una vez, y solo con servicios (mano de obra): los materiales
 *    ya descontaron stock al cerrar la orden.
 */
@Service
@Transactional
@Slf4j
public class OrdenTrabajoService {

    private final OrdenTrabajoRepository ordenRepository;
    private final ClienteService clienteService;
    private final SedeClienteService sedeService;
    private final ProductoService productoService;
    private final VentaService ventaService;
    private final MantenimientoService mantenimientoService;
    private final EquipoInstaladoService equipoService;

    public OrdenTrabajoService(OrdenTrabajoRepository ordenRepository, ClienteService clienteService,
                               SedeClienteService sedeService, ProductoService productoService, VentaService ventaService,
                               MantenimientoService mantenimientoService, EquipoInstaladoService equipoService) {
        this.ordenRepository = ordenRepository;
        this.clienteService = clienteService;
        this.sedeService = sedeService;
        this.productoService = productoService;
        this.ventaService = ventaService;
        this.mantenimientoService = mantenimientoService;
        this.equipoService = equipoService;
    }

    @Transactional(readOnly = true)
    public List<OrdenDTO.Response> listar(OrdenTrabajo.Estado estado, Long clienteId, String tecnico,
                                          LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new IllegalArgumentException("La fecha 'desde' no puede ser posterior a 'hasta'.");
        }
        String filtroTecnico = tecnico == null || tecnico.isBlank() ? null : tecnico.trim();
        return ordenRepository.buscar(estado, clienteId, filtroTecnico, desde, hasta).stream().map(this::aDto).toList();
    }

    @Transactional(readOnly = true)
    public OrdenDTO.Response obtener(Long id) {
        return aDto(buscar(id));
    }

    @Transactional(readOnly = true)
    public List<String> tecnicos() {
        return ordenRepository.tecnicos();
    }

    /** @param usuario preferred_username del token de quien la programa */
    public OrdenDTO.Response crear(OrdenDTO.Request dto, String usuario) {
        OrdenTrabajo o = new OrdenTrabajo();
        o.setRegistradoPor(usuario);
        aplicar(o, dto);
        OrdenTrabajo guardada = ordenRepository.save(o);
        if (guardada.getMantenimientoId() != null) {
            mantenimientoService.asignarTecnico(guardada.getMantenimientoId(), guardada.getTecnico());
        }
        log.info("Orden de trabajo {} programada: {} para {} el {} ({})", guardada.getId(), guardada.getTipo(),
                guardada.getCliente().getNombreORazonSocial(), guardada.getFechaProgramada(), guardada.getTecnico());
        return aDto(guardada);
    }

    public OrdenDTO.Response actualizar(Long id, OrdenDTO.Request dto) {
        OrdenTrabajo o = programada(id, "editar");
        aplicar(o, dto);
        OrdenTrabajo guardada = ordenRepository.save(o);
        if (guardada.getMantenimientoId() != null) {
            mantenimientoService.asignarTecnico(guardada.getMantenimientoId(), guardada.getTecnico());
        }
        return aDto(guardada);
    }

    /** Cierra la orden: lo hecho, quién lo recibió, materiales usados (descuentan stock) y equipos instalados. */
    public OrdenDTO.Response cerrar(Long id, OrdenDTO.Cierre dto, String usuario) {
        OrdenTrabajo o = programada(id, "cerrar");
        LocalDate fecha = dto.fechaRealizada() != null ? dto.fechaRealizada() : LocalDate.now();
        if (fecha.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La fecha en que se hizo el trabajo no puede ser futura.");
        }
        List<OrdenDTO.MaterialRequest> materiales = dto.materiales() != null ? dto.materiales() : List.of();
        List<OrdenDTO.EquipoInstaladoRequest> equipos = dto.equipos() != null ? dto.equipos() : List.of();
        if (!equipos.isEmpty() && o.getTipo() != OrdenTrabajo.Tipo.INSTALACION) {
            throw new IllegalArgumentException("Solo una orden de INSTALACIÓN registra equipos instalados.");
        }

        // ── Materiales: primero se valida todo y después se descuenta (nada a medias) ──
        Set<Long> vistos = new HashSet<>();
        List<Producto> productos = new java.util.ArrayList<>();
        for (OrdenDTO.MaterialRequest m : materiales) {
            if (!vistos.add(m.productoId())) {
                throw new IllegalArgumentException("El material #" + m.productoId() + " está repetido: sume las cantidades.");
            }
            Producto p = productoService.obtenerPorId(m.productoId());
            if (!p.controlaStock()) {
                throw new IllegalArgumentException("'" + p.getNombre() + "' no es un material con stock (es "
                        + p.getSector() + "): los equipos se registran como equipos instalados y la mano de obra se factura.");
            }
            int disponible = p.getStockActual() != null ? p.getStockActual() : 0;
            if (disponible < m.cantidad()) {
                throw new StockInsuficienteException(p.getNombre(), disponible, m.cantidad());
            }
            productos.add(p);
        }
        for (int i = 0; i < materiales.size(); i++) {
            Producto p = productos.get(i);
            p.setStockActual(p.getStockActual() - materiales.get(i).cantidad());
            productoService.guardar(p);
            o.agregarMaterial(new MaterialOrden(p, materiales.get(i).cantidad()));
        }

        o.setEstado(OrdenTrabajo.Estado.REALIZADA);
        o.setFechaRealizada(fecha);
        o.setTrabajoRealizado(dto.trabajoRealizado().trim());
        o.setRecibidoPor(dto.recibidoPor().trim());
        o.setObservaciones(vacioANulo(dto.observaciones()));
        o.setCerradoPor(usuario);
        OrdenTrabajo guardada = ordenRepository.save(o);

        for (OrdenDTO.EquipoInstaladoRequest e : equipos) {
            equipoService.registrarEquipo(new EquipoDTO.Registro(
                    guardada.getCliente().getId(),
                    guardada.getSede() != null ? guardada.getSede().getId() : null,
                    e.productoId(), e.numeroSerie(), e.ubicacion(), fecha, e.mesesGarantia(), e.frecuenciaMeses(),
                    guardada.getVenta() != null ? guardada.getVenta().getId() : null,
                    null), guardada.getId());
        }
        if (guardada.getMantenimientoId() != null) {
            mantenimientoService.realizarPorOrden(guardada.getMantenimientoId(), fecha, guardada.getTecnico(), guardada.getId());
        }
        log.info("Orden de trabajo {} realizada por {}: {} material(es), {} equipo(s) instalado(s)",
                id, guardada.getTecnico(), materiales.size(), equipos.size());
        return aDto(guardada);
    }

    public OrdenDTO.Response cancelar(Long id, String motivo) {
        OrdenTrabajo o = programada(id, "cancelar");
        o.setEstado(OrdenTrabajo.Estado.CANCELADA);
        o.setMotivoCancelacion(motivo.trim());
        return aDto(ordenRepository.save(o));
    }

    /**
     * Factura el servicio de una orden REALIZADA con una venta directa de mano de obra.
     * La venta calcula su detracción (es un servicio) y queda vinculada a la orden.
     */
    public OrdenDTO.Response facturar(Long id, OrdenDTO.Facturacion dto, String vendedorId, String vendedorNombre) {
        OrdenTrabajo o = buscar(id);
        if (o.getEstado() != OrdenTrabajo.Estado.REALIZADA) {
            throw new ReglaNegocioException("Solo se factura una orden REALIZADA. La #" + id + " está " + o.getEstado() + ".");
        }
        if (o.getVentaFacturadaId() != null) {
            throw new ReglaNegocioException("La orden #" + id + " ya se facturó en la venta #" + o.getVentaFacturadaId() + ".");
        }
        for (OrdenDTO.ItemFactura item : dto.items()) {
            Producto p = productoService.obtenerPorId(item.productoId());
            if (!p.esServicio()) {
                throw new IllegalArgumentException("'" + p.getNombre() + "' no es un servicio: la orden se factura con mano de obra "
                        + "(sector SERVICIO); los materiales ya se descontaron del stock al cerrarla.");
            }
        }
        VentaResponseDTO venta = ventaService.registrarVentaDirecta(
                o.getCliente().getId(),
                dto.items().stream().map(i -> new VentaService.ItemVentaDTO(i.productoId(), i.cantidad(), i.descuentoPorcentaje())).toList(),
                dto.tipoPago(), dto.diasCredito(), dto.moneda(), dto.tipoCambio(), vendedorId, vendedorNombre);
        o.setVentaFacturadaId(venta.id());
        log.info("Orden de trabajo {} facturada con la venta {}", id, venta.id());
        return aDto(ordenRepository.save(o));
    }

    /** Fachada para Rentabilidad: órdenes REALIZADAS en el periodo, con sus materiales. */
    @Transactional(readOnly = true)
    public List<OrdenTrabajo> realizadasEntre(LocalDate desde, LocalDate hasta) {
        return ordenRepository.realizadasEntre(desde, hasta);
    }

    /** Fachada para el kardex: materiales del producto usados en órdenes REALIZADAS. */
    @Transactional(readOnly = true)
    public List<MaterialOrden> materialesUsadosDeProducto(Long productoId) {
        return ordenRepository.materialesUsadosDeProducto(productoId);
    }

    // ── Ayudas ──────────────────────────────────────────────────

    /**
     * Agenda: un técnico no tiene dos visitas que se crucen el mismo día (409 con la orden que choca).
     * Solo se compara entre órdenes PROGRAMADAS que tienen hora; sin hora no hay cruce que detectar.
     */
    private void validarHorario(OrdenTrabajo o) {
        Integer inicio = o.minutoInicio();
        if (inicio == null) return;
        int fin = inicio + o.duracion();
        if (fin > 24 * 60) {
            throw new IllegalArgumentException("La visita terminaría después de medianoche: revise la hora o la duración.");
        }
        for (OrdenTrabajo otra : ordenRepository.programadasDelTecnico(o.getTecnico(), o.getFechaProgramada())) {
            if (otra.getId().equals(o.getId()) || otra.minutoInicio() == null) continue;
            int otraInicio = otra.minutoInicio();
            int otraFin = otraInicio + otra.duracion();
            if (inicio < otraFin && otraInicio < fin) {
                throw new ReglaNegocioException(o.getTecnico() + " ya tiene la orden #" + otra.getId() + " ("
                        + otra.getCliente().getNombreORazonSocial() + ") el " + otra.getFechaProgramada() + " de "
                        + otra.getHoraInicio() + " a " + hhmm(otraFin) + ". Elija otra hora u otro técnico.");
            }
        }
    }

    private static String hhmm(int minutos) {
        return String.format("%02d:%02d", minutos / 60, minutos % 60);
    }

    /** Agenda de técnicos: órdenes no canceladas entre dos fechas (máximo 6 semanas). */
    @Transactional(readOnly = true)
    public List<OrdenDTO.Response> agenda(LocalDate desde, LocalDate hasta) {
        if (desde.isAfter(hasta)) {
            throw new IllegalArgumentException("La fecha 'desde' no puede ser posterior a 'hasta'.");
        }
        if (desde.plusDays(42).isBefore(hasta)) {
            throw new IllegalArgumentException("La agenda muestra hasta 6 semanas a la vez.");
        }
        return ordenRepository.agenda(desde, hasta).stream().map(this::aDto).toList();
    }

    private void aplicar(OrdenTrabajo o, OrdenDTO.Request dto) {
        Cliente cliente = clienteService.obtenerPorId(dto.clienteId());
        SedeCliente sede = dto.sedeId() != null ? sedeService.obtenerDeCliente(cliente.getId(), dto.sedeId()) : null;

        Venta venta = null;
        if (dto.ventaId() != null) {
            venta = ventaService.obtenerPorIdPublico(dto.ventaId());
            if (!venta.getCliente().getId().equals(cliente.getId())) {
                throw new IllegalArgumentException("La venta #" + venta.getId() + " es de otro cliente.");
            }
            if (venta.getEstado() == Venta.EstadoVenta.ANULADA) {
                throw new ReglaNegocioException("La venta #" + venta.getId() + " está ANULADA.");
            }
        }

        EquipoInstalado equipo = dto.equipoId() != null ? equipoService.obtenerDeCliente(dto.equipoId(), cliente.getId()) : null;
        if (equipo != null && equipo.getEstado() == EquipoInstalado.Estado.RETIRADO) {
            throw new ReglaNegocioException("El equipo #" + equipo.getId() + " está RETIRADO.");
        }

        if (dto.mantenimientoId() != null) {
            if (dto.tipo() != OrdenTrabajo.Tipo.MANTENIMIENTO) {
                throw new IllegalArgumentException("Una orden que ejecuta una visita de mantenimiento es de tipo MANTENIMIENTO.");
            }
            Mantenimiento m = mantenimientoService.obtenerPendienteDeCliente(dto.mantenimientoId(), cliente.getId());
            boolean otraOrden = !dto.mantenimientoId().equals(o.getMantenimientoId())
                    && ordenRepository.existsByMantenimientoIdAndEstado(dto.mantenimientoId(), OrdenTrabajo.Estado.PROGRAMADA);
            if (otraOrden) {
                throw new ReglaNegocioException("La visita de mantenimiento #" + dto.mantenimientoId() + " ya tiene una orden programada.");
            }
            if (equipo == null && m.getEquipo() != null) {
                equipo = m.getEquipo();
            }
            if (sede == null && m.getEquipo() != null && m.getEquipo().getSede() != null) {
                sede = m.getEquipo().getSede();
            } else if (sede == null && m.getContrato() != null && m.getContrato().getSede() != null) {
                sede = m.getContrato().getSede();
            }
        }

        o.setTipo(dto.tipo());
        o.setCliente(cliente);
        o.setSede(sede);
        o.setVenta(venta);
        o.setEquipo(equipo);
        o.setMantenimientoId(dto.mantenimientoId());
        o.setFechaProgramada(dto.fechaProgramada());
        o.setTecnico(dto.tecnico().trim());
        o.setHoraInicio(vacioANulo(dto.horaInicio()));
        o.setDuracionMinutos(dto.duracionMinutos());
        validarHorario(o);
        o.setDescripcion(dto.descripcion().trim());
        String direccion = vacioANulo(dto.direccion());
        if (direccion == null) {
            direccion = sede != null && sede.getDireccion() != null ? sede.getDireccion() : cliente.getDireccion();
        }
        o.setDireccion(direccion);
    }

    private OrdenTrabajo programada(Long id, String accion) {
        OrdenTrabajo o = buscar(id);
        if (o.getEstado() != OrdenTrabajo.Estado.PROGRAMADA) {
            throw new ReglaNegocioException("No se puede " + accion + " la orden #" + id + ": está " + o.getEstado() + ".");
        }
        return o;
    }

    private OrdenTrabajo buscar(Long id) {
        return ordenRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Orden de trabajo no encontrada: " + id));
    }

    private OrdenDTO.Response aDto(OrdenTrabajo o) {
        return OrdenDTO.Response.desdeEntidad(o,
                o.getTipo() == OrdenTrabajo.Tipo.INSTALACION ? equipoService.deOrden(o.getId()) : List.of());
    }

    private static String vacioANulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
