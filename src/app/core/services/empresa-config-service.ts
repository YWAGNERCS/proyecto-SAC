import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ApiService } from './api-service';

export interface ConfiguracionEmpresa {
  whatsapp: string;
  cuentaBcp: string;
  cciBcp: string;
  cuentaNacion: string;
  actualizadoPor: string | null;
  fechaActualizacion: string | null;
}

@Injectable({ providedIn: 'root' })
export class EmpresaConfigService {
  private api = inject(ApiService);
  private http = inject(HttpClient);

  obtener() {
    return this.http.get<ConfiguracionEmpresa>(this.api.buildUrl('/v1/admin/empresa/configuracion'));
  }

  guardar(datos: { whatsapp: string; cuentaBcp: string; cciBcp: string; cuentaNacion: string }) {
    return this.http.put<ConfiguracionEmpresa>(this.api.buildUrl('/v1/admin/empresa/configuracion'), datos);
  }
}
