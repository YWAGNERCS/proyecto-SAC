package com.bomerp.compras.pago.entity;

import com.bomerp.compras.compra.entity.Compra;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Pago (total o parcial) de JD a un proveedor por una compra a crédito, en la moneda de la compra.
 * Uno mal registrado no se borra: se ANULA con motivo y su monto vuelve al saldo de la compra.
 */
@Entity
@Table(name = "PAGOS_PROVEEDOR")
@Getter
@Setter
@NoArgsConstructor
public class PagoProveedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "compra_id", nullable = false)
    private Compra compra;

    @Column(name = "fecha_pago", nullable = false)
    private LocalDate fechaPago;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Metodo metodo;

    @Column(name = "numero_operacion", length = 50)
    private String numeroOperacion;

    @Column(length = 300)
    private String observacion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Estado estado = Estado.VIGENTE;

    @Column(name = "fecha_anulacion")
    private LocalDate fechaAnulacion;

    @Column(name = "motivo_anulacion", length = 300)
    private String motivoAnulacion;

    @Column(name = "anulado_por", length = 100)
    private String anuladoPor;

    @Column(name = "registrado_por", length = 100)
    private String registradoPor;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro;

    public enum Metodo {
        TRANSFERENCIA, DEPOSITO, EFECTIVO, CHEQUE, YAPE_PLIN
    }

    public enum Estado {
        VIGENTE, ANULADO
    }
}
