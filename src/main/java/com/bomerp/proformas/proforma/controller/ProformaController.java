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

    /** Filtros opcionales: estado y cliente (el filtro "por cliente" de la pantalla). Más recientes primero. */
    @GetMapping
    public List<ProformaResponseDTO> listar(
            @RequestParam(required = false) Proforma.EstadoProforma estado,
            @RequestParam(required = false) Long clienteId) {
        return proformaService.listar(estado, clienteId);
    }

    @PostMapping
    public ProformaResponseDTO crear(@Valid @RequestBody ProformaRequest.CrearProformaDTO request) {
        return proformaService.crear(request);
    }

    /** Envía por WhatsApp y/o correo y devuelve qué salió y qué no. */
    @PostMapping("/{id}/enviar")
    public ProformaService.ResultadoEnvio enviar(@PathVariable Long id,
                        @RequestParam(defaultValue = "true") boolean whatsapp,
                        @RequestParam(defaultValue = "true") boolean correo) {
        return proformaService.enviar(id, whatsapp, correo);
    }

    @PostMapping("/{id}/aceptar")
    public ProformaResponseDTO aceptar(@PathVariable Long id) {
        return proformaService.aceptar(id);
    }

    /** Ejecuta a mano el vencimiento diario (normalmente corre solo a las 00:05 y al iniciar). */
    @PostMapping("/marcar-vencidas")
    public java.util.Map<String, Integer> marcarVencidas() {
        return java.util.Map.of("proformasVencidas", proformaService.marcarVencidas());
    }

    @PostMapping("/{id}/rechazar")
    public ProformaResponseDTO rechazar(@PathVariable Long id) {
        return proformaService.rechazar(id);
    }
}
