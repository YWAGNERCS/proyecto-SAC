package com.bomerp.compras.compra.entity;
import com.bomerp.compras.detallecompra.entity.DetalleCompra;


import com.bomerp.catalogo.producto.entity.Producto;
import com.bomerp.proveedores.proveedor.entity.Proveedor;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "COMPRAS")
@Getter
@Setter
@NoArgsConstructor
public class Compra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proveedor_id", nullable = false)
    private Proveedor proveedor;

    @Column(name = "fecha_compra", nullable = false)
    private LocalDate fechaCompra;

    @Column(name = "numero_factura", length = 50)
    private String numeroFactura;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Producto.Moneda moneda;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @OneToMany(mappedBy = "compra", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DetalleCompra> detalles = new ArrayList<>();

    /** Cuentas por pagar: al contado queda PAGADA; a crédito, PENDIENTE hasta cubrir el total con pagos. */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_pago", nullable = false, length = 10)
    private TipoPago tipoPago = TipoPago.CONTADO;

    @Column(name = "dias_credito")
    private Integer diasCredito;

    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_pago", nullable = false, length = 10)
    private EstadoPago estadoPago = EstadoPago.PAGADA;

    /** Usuario de Keycloak que la registró (null en las anteriores). */
    @Column(name = "registrado_por", length = 100)
    private String registradoPor;

    public enum TipoPago {
        CONTADO, CREDITO
    }

    public enum EstadoPago {
        PAGADA, PENDIENTE
    }

    public void addDetalle(DetalleCompra detalle) {
        detalles.add(detalle);
        detalle.setCompra(this);
    }
}
