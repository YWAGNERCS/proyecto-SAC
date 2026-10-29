package com.bomerp.clientes.sede.dto;

import com.bomerp.clientes.sede.entity.SedeCliente;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Sede de un cliente: lo que se envía al crearla o editarla y lo que se devuelve. */
public final class SedeDTO {

    private SedeDTO() {
    }

    public record Request(
            @NotBlank(message = "Indique el nombre de la sede") @Size(max = 100) String nombre,
            @Size(max = 200) String direccion,
            @Size(max = 100) String contacto,
            @Size(max = 20) String telefono
    ) {}

    public record Response(Long id, Long clienteId, String nombre, String direccion, String contacto, String telefono) {

        public static Response desdeEntidad(SedeCliente s) {
            return new Response(s.getId(), s.getCliente().getId(), s.getNombre(), s.getDireccion(),
                    s.getContacto(), s.getTelefono());
        }
    }
}
