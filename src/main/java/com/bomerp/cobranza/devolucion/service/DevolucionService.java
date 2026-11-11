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
import com.bomerp.clientes.general.integration.Cliente;
import jakarta.persistence.EntityManager;
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
    /** Referencias a la venta y al cliente por su id, sin consultarlos (ADR-008). */
    private final EntityManager em;

    public DevolucionService(DevolucionRepository devolucionRepository, CobranzaService cobranzaService,
                             EntityManager em) {
        this.devolucionRepository = devolucionRepository;
        this.cobranzaService = cobranzaService;
        this.em = em;
    }

    /**
     * Corre dentro de la transacción de la anulación: si no se puede guardar la devolución,
     * la venta tampoco queda anulada.
     */
    @EventListener
    public void alAnularVenta(VentaAnuladaEvent evento) {
        // ADR-008: todo sale del evento. Ya no se le pregunta a Ventas por la venta anulada; lo pagado es dato de
        // Cobranza (sus abonos) o, si se pagó al contado, el total que trae el evento.
        BigDecimal pagado = cobranzaService.pagadoDe(evento.ventaId(), evento.total(), evento.pagada());
        if (pagado.signum() <= 0 || devolucionRepository.existsByVentaId(evento.ventaId())) {
            return;
        }
        Devolucion devolucion = new Devolucion();
        devolucion.setVenta(em.getReference(Venta.class, evento.ventaId()));
        devolucion.setCliente(em.getReference(Cliente.class, evento.clienteId()));
        devolucion.setMoneda(evento.moneda());
        devolucion.setMonto(pagado);
        devolucion.setFechaAnulacion(evento.fechaAnulacion() != null ? evento.fechaAnulacion() : LocalDate.now());
        devolucionRepository.save(devolucion);
        log.info("Venta {} ({}) anulada con {} {} ya pagados: devolución pendiente", evento.ventaId(),
                evento.documento() != null ? evento.documento() : "sin comprobante", pagado, evento.moneda());
    }

    @Transactional(readOnly = true)
    public List<DevolucionResponseDTO> listar(Devolucion.Estado estado, Long clienteId) {
        return devolucionRepository.buscar(estado, clienteId).stream().map(DevolucionResponseDTO::desdeEntidad).toList();
    }

    /** PENDIENTE -> DEVUELTA. Fecha: hoy por defecto; ni futura ni anterior a la anulación. */
    public DevolucionResponseDTO registrar(Long id, DevolucionRequestDTO dto) {
        // Bloqueada (revisión externa): dos registros simultáneos no pisan la fecha, el método ni el N° de operación
        Devolucion devolucion = devolucionRepository.bloquear(id)
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
