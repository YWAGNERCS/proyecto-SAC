package com.jdrefrigeracion.clientes.integrations;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "CLIENTES")
@Getter
@Setter
@NoArgsConstructor
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoCliente tipo;

    @NotBlank
    @Column(nullable = false, length = 150)
    private String nombreORazonSocial;

    /** Solo aplica si tipo = EMPRESA */
    @Column(length = 11)
    private String ruc;

    /** Solo aplica si tipo = PERSONA_NATURAL */
    @Column(length = 8)
    private String dni;

    @Column(length = 200)
    private String direccion;

    @Column(length = 20)
    private String telefono;

    @Column(length = 100)
    private String correo;

    public enum TipoCliente {
        PERSONA_NATURAL, EMPRESA
    }
}
