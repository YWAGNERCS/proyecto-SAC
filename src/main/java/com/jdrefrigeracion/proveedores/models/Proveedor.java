package com.jdrefrigeracion.proveedores.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "PROVEEDORES")
@Getter
@Setter
@NoArgsConstructor
public class Proveedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoProveedor tipo;

    @NotBlank
    @Column(name = "nombre_o_razon_social", nullable = false, length = 150)
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
    
    @Column(name = "contacto_vendedor", length = 150)
    private String contactoVendedor;

    public enum TipoProveedor {
        PERSONA_NATURAL, EMPRESA
    }
}
