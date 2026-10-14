package com.bomerp.proformas.proforma.controller;
import com.bomerp.proformas.proformarequest.entity.ProformaRequest;
import com.bomerp.proformas.proforma.entity.Proforma;
import com.bomerp.proformas.proforma.service.ProformaService;
import com.bomerp.proformas.proforma.dto.ProformaResponseDTO;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/proformas")
public class ProformaController {

    private final ProformaService proformaService;

    public ProformaController(ProformaService proformaService) {
        this.proformaService = proformaService;
    }

    @GetMapping
    public List<ProformaResponseDTO> listar(
            @RequestParam(required = false) Proforma.EstadoProforma estado) {
        List<Proforma> proformas = estado != null
                ? proformaService.listarPorEstado(estado)
                : proformaService.listarTodas();
        return proformas.stream().map(ProformaResponseDTO::desdeEntidad).toList();
    }

    @PostMapping
    public ProformaResponseDTO crear(@Valid @RequestBody ProformaRequest.CrearProformaDTO request) {
        Proforma creada = proformaService.crear(request);
        return ProformaResponseDTO.desdeEntidad(creada);
    }

    @PostMapping("/{id}/enviar")
    public void enviar(@PathVariable Long id,
                        @RequestParam(defaultValue = "true") boolean whatsapp,
                        @RequestParam(defaultValue = "true") boolean correo) {
        proformaService.enviar(id, whatsapp, correo);
    }

    @PostMapping("/{id}/aceptar")
    public ProformaResponseDTO aceptar(@PathVariable Long id) {
        return ProformaResponseDTO.desdeEntidad(proformaService.aceptar(id));
    }

    @PostMapping("/{id}/rechazar")
    public ProformaResponseDTO rechazar(@PathVariable Long id) {
        return ProformaResponseDTO.desdeEntidad(proformaService.rechazar(id));
    }
}
