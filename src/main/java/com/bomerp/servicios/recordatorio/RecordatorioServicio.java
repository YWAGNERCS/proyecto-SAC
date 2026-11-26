package com.bomerp.servicios.recordatorio;

import com.bomerp.servicios.orden.entity.OrdenTrabajo;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;

@Entity @Table(name="RECORDATORIOS_SERVICIO",uniqueConstraints=@UniqueConstraint(name="UK_RECORDATORIO_ORDEN",columnNames="orden_id"))
@Getter @Setter @NoArgsConstructor
public class RecordatorioServicio {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch=FetchType.LAZY) @JoinColumn(name="orden_id",nullable=false) private OrdenTrabajo orden;
    @Convert(converter=org.hibernate.type.NumericBooleanConverter.class)
    private boolean activo;
    private int frecuenciaMeses;
    private int anticipacionDias;
    private LocalDate fechaSiguiente;
    private LocalDate fechaAviso;
    @Column(length=150) private String correoCliente;
    @Column(length=150) private String correoOficina;
    @Column(length=12) private String estadoCliente="PENDIENTE";
    @Column(length=12) private String estadoOficina="PENDIENTE";
    private int intentosCliente;
    private int intentosOficina;
    private LocalDateTime intentoCliente;
    private LocalDateTime intentoOficina;
    @Column(length=500) private String errorCliente;
    @Column(length=500) private String errorOficina;
}
