package com.bomerp.postventa.mantenimiento.service;

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
