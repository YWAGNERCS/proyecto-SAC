package com.jdrefrigeracion.proveedores.services;
import com.jdrefrigeracion.proveedores.models.Proveedor;
import com.jdrefrigeracion.proveedores.repositories.ProveedorRepository;
import com.jdrefrigeracion.clientes.integrations.ConsultaRucClient;

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

    public Proveedor guardar(Proveedor proveedor) {
        return proveedorRepository.save(proveedor);
    }

    public void eliminar(Long id) {
        proveedorRepository.deleteById(id);
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
}
