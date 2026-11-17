package com.bomerp.config.empresa;

import java.time.LocalDateTime;

public record ConfiguracionEmpresaDTO(
        String whatsapp,
        String cuentaBcp,
        String cciBcp,
        String cuentaNacion,
        String cuentaDetracciones,
        String actualizadoPor,
        LocalDateTime fechaActualizacion
) {}
