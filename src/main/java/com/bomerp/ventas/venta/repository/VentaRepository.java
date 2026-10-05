package com.bomerp.ventas.venta.repository;
import com.bomerp.ventas.venta.entity.Venta;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface VentaRepository extends JpaRepository<Venta, Long> {
    List<Venta> findByEstadoPago(Venta.EstadoPago estadoPago);

    // Para el reporte de cobranza: ventas a crédito con pago vencido
    List<Venta> findByEstadoPagoAndFechaLimitePagoBefore(
            Venta.EstadoPago estadoPago, LocalDate fecha);

    List<Venta> findByFechaVentaBetween(LocalDate desde, LocalDate hasta);
}
