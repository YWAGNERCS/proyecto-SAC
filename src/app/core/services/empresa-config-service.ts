import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ApiService } from './api-service';

export interface ConfiguracionEmpresa {
  whatsapp: string | null;
  cuentaBcp: string | null;
  cciBcp: string | null;
  cuentaNacion: string | null;
  cuentaDetracciones: string | null;
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

  guardarWhatsapp(whatsapp: string) {
    return this.http.put<ConfiguracionEmpresa>(this.api.buildUrl('/v1/admin/empresa/configuracion'), { whatsapp });
  }
}
