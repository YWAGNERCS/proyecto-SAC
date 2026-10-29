package com.bomerp.clientes.cliente.service;
import com.bomerp.clientes.consultaruc.integration.ConsultaRucClient;
import com.bomerp.config.RecursoNoEncontradoException;
import com.bomerp.config.ReglaNegocioException;
import com.bomerp.clientes.general.integration.Cliente;
import com.bomerp.clientes.cliente.repository.ClienteRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final ConsultaRucClient consultaRucClient; // apis.net.pe con respaldo (ver ConsultaRucClientConRespaldo)

    public ClienteService(ClienteRepository clienteRepository, ConsultaRucClient consultaRucClient) {
        this.clienteRepository = clienteRepository;
        this.consultaRucClient = consultaRucClient;
    }

    public List<Cliente> listarTodos() {
        return clienteRepository.findAll();
    }

    /** Crea un cliente nuevo, validando tipo, documento, duplicados y correo. */
    @Transactional
    public Cliente guardar(Cliente cliente) {
        cliente.setId(null);
        validar(cliente);
        return clienteRepository.save(cliente);
    }

    @Transactional
    public Cliente actualizar(Long id, Cliente datos) {
        Cliente cliente = obtenerPorId(id);
        cliente.setTipo(datos.getTipo());
        cliente.setNombreORazonSocial(datos.getNombreORazonSocial());
        cliente.setRuc(datos.getRuc());
        cliente.setDni(datos.getDni());
        cliente.setDireccion(datos.getDireccion());
        cliente.setTelefono(datos.getTelefono());
        cliente.setCorreo(datos.getCorreo());
        cliente.setAgenteRetencion(datos.isAgenteRetencion());
        validar(cliente);
        return clienteRepository.save(cliente);
    }

    /** 409 si el cliente ya tiene proformas o ventas: se conserva el historial. */
    @Transactional
    public void eliminar(Long id) {
        Cliente cliente = obtenerPorId(id);
        try {
            clienteRepository.delete(cliente);
            clienteRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ReglaNegocioException("No se puede eliminar a '" + cliente.getNombreORazonSocial()
                    + "': tiene proformas o ventas registradas.");
        }
    }

    private static final String FORMATO_CORREO = "^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$";

    private void validar(Cliente c) {
        if (c.getTipo() == null) {
            throw new IllegalArgumentException("Indique si el cliente es empresa o persona natural.");
        }
        c.setNombreORazonSocial(c.getNombreORazonSocial().trim());
        c.setRuc(vacioANull(c.getRuc()));
        c.setDni(vacioANull(c.getDni()));
        c.setCorreo(vacioANull(c.getCorreo()));

        if (c.getTipo() == Cliente.TipoCliente.EMPRESA) {
            if (c.getRuc() == null || !c.getRuc().matches("\\d{11}")) {
                throw new IllegalArgumentException("Una empresa debe tener un RUC de 11 dígitos.");
            }
            c.setDni(null);
            boolean duplicado = c.getId() == null
                    ? clienteRepository.existsByRuc(c.getRuc())
                    : clienteRepository.existsByRucAndIdNot(c.getRuc(), c.getId());
            if (duplicado) {
                throw new ReglaNegocioException("Ya existe un cliente con el RUC " + c.getRuc() + ".");
            }
        } else {
            if (c.getDni() == null || !c.getDni().matches("\\d{8}")) {
                throw new IllegalArgumentException("Una persona natural debe tener un DNI de 8 dígitos.");
            }
            c.setRuc(null);
            boolean duplicado = c.getId() == null
                    ? clienteRepository.existsByDni(c.getDni())
                    : clienteRepository.existsByDniAndIdNot(c.getDni(), c.getId());
            if (duplicado) {
                throw new ReglaNegocioException("Ya existe un cliente con el DNI " + c.getDni() + ".");
            }
        }
        if (c.getCorreo() != null && !c.getCorreo().matches(FORMATO_CORREO)) {
            throw new IllegalArgumentException("El correo no tiene un formato válido.");
        }
    }

    private static String vacioANull(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
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
        Optional<Cliente> existente = clienteRepository.findFirstByRuc(ruc);
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
        Optional<Cliente> existente = clienteRepository.findFirstByDni(dni);
        if (existente.isPresent()) {
            return existente.get();
        }

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

    /** Fachada pública para otros módulos: evita que accedan a ClienteRepository. */
    public Cliente obtenerPorId(Long id) {
        return clienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cliente no encontrado con ID: " + id));
    }
}
