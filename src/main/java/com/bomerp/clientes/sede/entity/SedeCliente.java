package com.bomerp.clientes.sede.entity;

import com.bomerp.clientes.general.integration.Cliente;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Local de un cliente donde JD instala y mantiene equipos (p. ej. "Tottus Breña").
 * Un cliente corporativo tiene varias sedes; un cliente con un solo local puede no registrar ninguna.
 */
@Entity
@Table(name = "SEDES_CLIENTE")
@Getter
@Setter
@NoArgsConstructor
public class SedeCliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(length = 200)
    private String direccion;

    /** Persona a quien llamar en el local (jefe de mantenimiento, administrador). */
    @Column(length = 100)
    private String contacto;

    @Column(length = 20)
    private String telefono;
}
