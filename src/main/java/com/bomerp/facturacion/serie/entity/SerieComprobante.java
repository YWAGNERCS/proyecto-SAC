package com.bomerp.facturacion.serie.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Último número emitido de cada serie (F001 facturas, B001 boletas, FC01/BC01 notas de crédito).
 * SUNAT exige numeración correlativa por serie: empieza en 1, sin saltos ni repetidos.
 */
@Entity
@Table(name = "SERIES_COMPROBANTE")
@Getter
@Setter
@NoArgsConstructor
public class SerieComprobante {

    @Id
    @Column(length = 4)
    private String serie;

    @Column(name = "ultimo_numero", nullable = false)
    private long ultimoNumero;

    public SerieComprobante(String serie, long ultimoNumero) {
        this.serie = serie;
        this.ultimoNumero = ultimoNumero;
    }
}
