package com.bomerp.cobranza.devolucion.service;

import com.bomerp.cobranza.cobro.service.CobranzaService;
import com.bomerp.cobranza.devolucion.dto.DevolucionRequestDTO;
import com.bomerp.cobranza.devolucion.dto.DevolucionResponseDTO;
import com.bomerp.cobranza.devolucion.entity.Devolucion;
import com.bomerp.cobranza.devolucion.repository.DevolucionRepository;
import com.bomerp.config.RecursoNoEncontradoException;
import com.bomerp.config.ReglaNegocioException;
import com.bomerp.ventas.venta.entity.Venta;
import com.bomerp.ventas.venta.event.VentaAnuladaEvent;
import com.bomerp.ventas.venta.service.VentaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Devoluciones por ventas anuladas (auditoría A8): lo que el cliente pagó de una venta
 * que luego se anuló queda PENDIENTE de devolver hasta que se registra cómo se devolvió.
 */
@Service
@Transactional
@Slf4j
public class DevolucionService {

    private final DevolucionRepository devolucionRepository;
    private final CobranzaService cobranzaService;
    private final VentaService ventaService;

    public DevolucionService(DevolucionRepository devolucionRepository, CobranzaService cobranzaService,
                             VentaService ventaService) {
        this.devolucionRepository = devolucionRepository;
        this.cobranzaService = cobranzaService;
        this.ventaService = ventaService;
    }

    /**
     * Corre dentro de la transacción de la anulación: si no se puede guardar la devolución,
     * la venta tampoco queda anulada.
     */
    @EventListener
    public void alAnularVenta(VentaAnuladaEvent evento) {
        Venta venta = ventaService.obtenerPorIdPublico(evento.ventaId());
        BigDecimal pagado = cobranzaService.pagadoDe(venta);
        if (pagado.signum() <= 0 || devolucionRepository.existsByVentaId(venta.getId())) {
            return;
        }
        Devolucion devolucion = new Devolucion();
        devolucion.setVenta(venta);
        devolucion.setCliente(venta.getCliente());
        devolucion.setMoneda(venta.getMoneda());
        devolucion.setMonto(pagado);
        devolucion.setFechaAnulacion(venta.getFechaAnulacion() != null ? venta.getFechaAnulacion() : LocalDate.now());
        devolucionRepository.save(devolucion);
        log.info("Venta {} anulada con {} {} ya pagados: devolución pendiente", venta.getId(), pagado, venta.getMoneda());
    }

    @Transactional(readOnly = true)
    public List<DevolucionResponseDTO> listar(Devolucion.Estado estado, Long clienteId) {
        return devolucionRepository.buscar(estado, clienteId).stream().map(DevolucionResponseDTO::desdeEntidad).toList();
    }

    /** PENDIENTE -> DEVUELTA. Fecha: hoy por defecto; ni futura ni anterior a la anulación. */
    public DevolucionResponseDTO registrar(Long id, DevolucionRequestDTO dto) {
        Devolucion devolucion = devolucionRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Devolución no encontrada: " + id));
        if (devolucion.getEstado() == Devolucion.Estado.DEVUELTA) {
            throw new ReglaNegocioException("La devolución #" + id + " ya se registró el "
                    + devolucion.getFechaDevolucion() + ".");
        }
        LocalDate fecha = dto.fecha() != null ? dto.fecha() : LocalDate.now();
        if (fecha.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La fecha de la devolución no puede ser futura.");
        }
        if (fecha.isBefore(devolucion.getFechaAnulacion())) {
            throw new IllegalArgumentException("La fecha de la devolución no puede ser anterior a la anulación ("
                    + devolucion.getFechaAnulacion() + ").");
        }
        devolucion.setEstado(Devolucion.Estado.DEVUELTA);
        devolucion.setFechaDevolucion(fecha);
        devolucion.setMetodoPago(dto.metodoPago());
        devolucion.setReferencia(vacioANulo(dto.referencia()));
        devolucion.setNotas(vacioANulo(dto.notas()));
        log.info("Devolución {} registrada: {} {} a {}", id, devolucion.getMonto(), devolucion.getMoneda(),
                devolucion.getCliente().getNombreORazonSocial());
        return DevolucionResponseDTO.desdeEntidad(devolucion);
    }

    private static String vacioANulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
