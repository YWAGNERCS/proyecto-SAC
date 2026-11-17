package com.bomerp.servicios.orden.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Fila de coordinación para las reservas con hora de un técnico en un día. */
@Entity
@Table(name = "RESERVAS_AGENDA")
public class ReservaAgenda {
    @Id
    @Column(name = "CLAVE", length = 120)
    private String clave;

    protected ReservaAgenda() {}
}
