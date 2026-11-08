package com.bomerp.seguridad.descuento.datos;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Topes de descuento por línea que Lisandro fija en Configuración (una sola fila, ID = 1). Sin fila se usan los de
 * application.properties (app.descuentos.*). Solo el ADMIN los cambia; el cambio queda en el historial.
 */
@Entity
@Table(name = "TOPES_DESCUENTO")
@Getter
@Setter
@NoArgsConstructor
public class TopesDescuento {

    public static final long ID_UNICO = 1L;

    @Id
    private Long id = ID_UNICO;

    /** Tope del administrador y de quien tiene el permiso "Descuento ampliado" (%). */
    @Column(name = "tope_admin", nullable = false, precision = 5, scale = 2)
    private BigDecimal topeAdmin;

    /** Tope de los asistentes (%). */
    @Column(name = "tope_asistente", nullable = false, precision = 5, scale = 2)
    private BigDecimal topeAsistente;

    @Column(name = "actualizado_por", length = 100)
    private String actualizadoPor;

    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;
}
