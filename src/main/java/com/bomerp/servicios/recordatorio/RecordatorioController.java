package com.bomerp.servicios.recordatorio;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
@RestController
@RequestMapping("/api/v1/admin/ordenes/{id}/recordatorio")
public class RecordatorioController {
    private final RecordatorioService servicio;
    public RecordatorioController(RecordatorioService servicio){this.servicio=servicio;}
    @GetMapping public RecordatorioService.Estado obtener(@PathVariable Long id){return servicio.obtener(id);}
    @PutMapping public RecordatorioService.Estado configurar(@PathVariable Long id,@Valid @RequestBody RecordatorioService.Config c){return servicio.configurar(id,c);}
    @PostMapping("/reintentar") public RecordatorioService.Estado reintentar(@PathVariable Long id,@RequestParam(defaultValue="false") boolean confirmar){return servicio.reintentar(id,confirmar);}
}
