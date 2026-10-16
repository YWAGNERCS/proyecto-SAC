package com.bomerp.proveedores.proveedor.service;
import com.bomerp.config.ReglaNegocioException;
import org.springframework.dao.DataIntegrityViolationException;
import com.bomerp.proveedores.proveedor.entity.Proveedor;
import com.bomerp.proveedores.proveedor.repository.ProveedorRepository;
import com.bomerp.clientes.consultaruc.integration.ConsultaRucClient;
import com.bomerp.config.RecursoNoEncontradoException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ProveedorService {

    private final ProveedorRepository proveedorRepository;
    private final ConsultaRucClient consultaRucClient;

    public ProveedorService(ProveedorRepository proveedorRepository, ConsultaRucClient consultaRucClient) {
        this.proveedorRepository = proveedorRepository;
        this.consultaRucClient = consultaRucClient;
    }

    public List<Proveedor> listarTodos() {
        return proveedorRepository.findAll();
    }

    public Optional<Proveedor> buscarPorId(Long id) {
        return proveedorRepository.findById(id);
    }

    /** Valida tipo, documento, duplicados y correo antes de guardar (crear o editar). */
    public Proveedor guardar(Proveedor proveedor) {
        validar(proveedor);
        return proveedorRepository.save(proveedor);
    }

    /** 409 si el proveedor ya tiene compras registradas: se conserva el historial. */
    public void eliminar(Long id) {
        try {
            proveedorRepository.deleteById(id);
            proveedorRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ReglaNegocioException("No se puede eliminar el proveedor: tiene compras registradas.");
        }
    }

    private static final String FORMATO_CORREO = "^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$";

    private void validar(Proveedor p) {
        if (p.getTipo() == null) {
            throw new IllegalArgumentException("Indique si el proveedor es empresa o persona natural.");
        }
        p.setNombreORazonSocial(p.getNombreORazonSocial().trim());
        p.setRuc(vacioANull(p.getRuc()));
        p.setDni(vacioANull(p.getDni()));
        p.setCorreo(vacioANull(p.getCorreo()));

        if (p.getTipo() == Proveedor.TipoProveedor.EMPRESA) {
            if (p.getRuc() == null || !p.getRuc().matches("\\d{11}")) {
                throw new IllegalArgumentException("Una empresa debe tener un RUC de 11 dígitos.");
            }
            p.setDni(null);
            boolean duplicado = p.getId() == null
                    ? proveedorRepository.existsByRuc(p.getRuc())
                    : proveedorRepository.existsByRucAndIdNot(p.getRuc(), p.getId());
            if (duplicado) {
                throw new ReglaNegocioException("Ya existe un proveedor con el RUC " + p.getRuc() + ".");
            }
        } else {
            if (p.getDni() == null || !p.getDni().matches("\\d{8}")) {
                throw new IllegalArgumentException("Una persona natural debe tener un DNI de 8 dígitos.");
            }
            p.setRuc(null);
            boolean duplicado = p.getId() == null
                    ? proveedorRepository.existsByDni(p.getDni())
                    : proveedorRepository.existsByDniAndIdNot(p.getDni(), p.getId());
            if (duplicado) {
                throw new ReglaNegocioException("Ya existe un proveedor con el DNI " + p.getDni() + ".");
            }
        }
        if (p.getCorreo() != null && !p.getCorreo().matches(FORMATO_CORREO)) {
            throw new IllegalArgumentException("El correo no tiene un formato válido.");
        }
    }

    private static String vacioANull(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    public Proveedor autocompletarPorRuc(String ruc) {
        Optional<Proveedor> existente = proveedorRepository.findByRuc(ruc);
        if (existente.isPresent()) {
            return existente.get();
        }

        ConsultaRucClient.DatosRuc datos = consultaRucClient.consultar(ruc);
        if (datos == null) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "No se encontraron datos para el RUC: " + ruc);
        }

        Proveedor nuevo = new Proveedor();
        nuevo.setTipo(Proveedor.TipoProveedor.EMPRESA);
        nuevo.setRuc(datos.ruc());
        nuevo.setNombreORazonSocial(datos.razonSocial());
        nuevo.setDireccion(datos.direccion());
        return nuevo;
    }

    public Proveedor autocompletarPorDni(String dni) {
        Optional<Proveedor> existente = proveedorRepository.findFirstByDni(dni);
        if (existente.isPresent()) {
            return existente.get();
        }

        ConsultaRucClient.DatosDni datos = consultaRucClient.consultarDni(dni);
        if (datos == null) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "No se encontraron datos para el DNI: " + dni);
        }

        Proveedor nuevo = new Proveedor();
        nuevo.setTipo(Proveedor.TipoProveedor.PERSONA_NATURAL);
        nuevo.setDni(datos.dni());
        nuevo.setNombreORazonSocial(datos.nombres() + " " + datos.apellidoPaterno() + " " + datos.apellidoMaterno());
        return nuevo;
    }

    /** Fachada pública para otros módulos: evita que accedan a ProveedorRepository. */
    public Proveedor obtenerPorId(Long id) {
        return proveedorRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Proveedor no encontrado con ID: " + id));
    }
}
