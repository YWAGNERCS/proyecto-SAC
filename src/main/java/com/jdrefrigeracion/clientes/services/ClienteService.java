package com.jdrefrigeracion.clientes.services;
import com.jdrefrigeracion.clientes.integrations.ConsultaRucClient;
import com.jdrefrigeracion.clientes.integrations.Cliente;
import com.jdrefrigeracion.clientes.repositories.ClienteRepository;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final ConsultaRucClient consultaRucClient; // implementación real se conecta más adelante

    public ClienteService(ClienteRepository clienteRepository, ConsultaRucClient consultaRucClient) {
        this.clienteRepository = clienteRepository;
        this.consultaRucClient = consultaRucClient;
    }

    public List<Cliente> listarTodos() {
        return clienteRepository.findAll();
    }

    public Cliente guardar(Cliente cliente) {
        return clienteRepository.save(cliente);
    }

    /**
     * RF confirmado con Lisandro: autocompletar datos de la empresa
     * cuando se ingresa un RUC (70% de sus ventas son B2B).
     *
     * Si el cliente ya existe en la BD, lo devuelve directo.
     * Si no existe, consulta el RUC externamente y arma un Cliente
     * "borrador" listo para guardar (aún no se persiste).
     */
    public Cliente autocompletarPorRuc(String ruc) {
        Optional<Cliente> existente = clienteRepository.findByRuc(ruc);
        if (existente.isPresent()) {
            return existente.get();
        }

        ConsultaRucClient.DatosRuc datos = consultaRucClient.consultar(ruc);
        if (datos == null) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "No se encontraron datos para el RUC: " + ruc);
        }

        Cliente nuevo = new Cliente();
        nuevo.setTipo(Cliente.TipoCliente.EMPRESA);
        nuevo.setRuc(datos.ruc());
        nuevo.setNombreORazonSocial(datos.razonSocial());
        nuevo.setDireccion(datos.direccion());
        return nuevo; // el usuario confirma y recién ahí se guarda (POST aparte)
    }

    public Cliente autocompletarPorDni(String dni) {
        ConsultaRucClient.DatosDni datos = consultaRucClient.consultarDni(dni);
        if (datos == null) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "No se encontraron datos para el DNI: " + dni);
        }

        Cliente nuevo = new Cliente();
        nuevo.setTipo(Cliente.TipoCliente.PERSONA_NATURAL);
        nuevo.setDni(datos.dni());
        nuevo.setNombreORazonSocial(datos.nombres() + " " + datos.apellidoPaterno() + " " + datos.apellidoMaterno());
        return nuevo;
    }
}
