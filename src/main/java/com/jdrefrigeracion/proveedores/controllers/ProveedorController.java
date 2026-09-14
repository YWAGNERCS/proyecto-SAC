package com.jdrefrigeracion.proveedores.controllers;
import com.jdrefrigeracion.proveedores.models.Proveedor;
import com.jdrefrigeracion.proveedores.services.ProveedorService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/admin/proveedores")
public class ProveedorController {

    private final ProveedorService proveedorService;

    public ProveedorController(ProveedorService proveedorService) {
        this.proveedorService = proveedorService;
    }

    @GetMapping
    public List<Proveedor> listar() {
        return proveedorService.listarTodos();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Proveedor> buscarPorId(@PathVariable Long id) {
        return proveedorService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Proveedor crear(@Valid @RequestBody Proveedor proveedor) {
        // En una app real, aquí se validarían duplicados por RUC, etc.
        return proveedorService.guardar(proveedor);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Proveedor> actualizar(@PathVariable Long id, @Valid @RequestBody Proveedor proveedorActualizado) {
        return proveedorService.buscarPorId(id)
                .map(proveedorExistente -> {
                    proveedorExistente.setNombreORazonSocial(proveedorActualizado.getNombreORazonSocial());
                    proveedorExistente.setTipo(proveedorActualizado.getTipo());
                    proveedorExistente.setRuc(proveedorActualizado.getRuc());
                    proveedorExistente.setDni(proveedorActualizado.getDni());
                    proveedorExistente.setDireccion(proveedorActualizado.getDireccion());
                    proveedorExistente.setTelefono(proveedorActualizado.getTelefono());
                    proveedorExistente.setCorreo(proveedorActualizado.getCorreo());
                    proveedorExistente.setContactoVendedor(proveedorActualizado.getContactoVendedor());
                    return ResponseEntity.ok(proveedorService.guardar(proveedorExistente));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        return proveedorService.buscarPorId(id)
                .map(proveedor -> {
                    proveedorService.eliminar(id);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/autocompletar/ruc/{ruc}")
    public Proveedor autocompletarPorRuc(@PathVariable String ruc) {
        return proveedorService.autocompletarPorRuc(ruc);
    }

    @GetMapping("/autocompletar/dni/{dni}")
    public Proveedor autocompletarPorDni(@PathVariable String dni) {
        return proveedorService.autocompletarPorDni(dni);
    }
}
