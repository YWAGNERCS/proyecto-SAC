import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { ApiService } from './api-service';
import { Permiso } from '../auth/auth.service';

export interface PermisoCatalogo {
  codigo: Permiso;
  nombre: string;
  descripcion: string;
}

export interface UsuarioPermisos {
  username: string;
  nombre: string;
  email: string;
  habilitado: boolean;
  rol: 'ADMIN' | 'ASISTENTE' | 'SIN_ROL';
  permisos: Permiso[];
}

export type ResultadoCambio = 'EXITO' | 'DENEGADO' | 'RECHAZADO' | 'ERROR';

export interface RegistroCambio {
  id: number;
  fechaHora: string;
  usuario: string;
  roles: string | null;
  metodo: string;
  ruta: string;
  estadoHttp: number;
  resultado: ResultadoCambio;
  modulo: string;
  accion: string;
  detalle: string | null;
  ip: string | null;
}

export interface Historial {
  registros: RegistroCambio[];
  /** Hay más de los que se muestran (tope de 500): conviene filtrar. */
  truncado: boolean;
  usuarios: string[];
  modulos: string[];
}

export interface EventoSesion {
  fechaHora: string;
  tipo: 'LOGIN' | 'LOGIN_ERROR' | 'LOGOUT';
  usuario: string;
  ip: string | null;
  error: string | null;
}

export interface FiltroHistorial {
  usuario?: string;
  modulo?: string;
  resultado?: ResultadoCambio | '';
  desde?: string;
  hasta?: string;
}

/** Permisos delegables e historial de cambios y sesiones (solo ADMIN; el backend lo exige). */
@Injectable({ providedIn: 'root' })
export class SeguridadService {
  private api = inject(ApiService);
  private http = inject(HttpClient);

  permisos() {
    return this.http.get<{ catalogo: PermisoCatalogo[]; usuarios: UsuarioPermisos[] }>(
      this.api.buildUrl('/v1/admin/permisos'),
    );
  }

  guardarPermisos(username: string, permisos: Permiso[]) {
    return this.http.put<UsuarioPermisos>(this.api.buildUrl(`/v1/admin/permisos/${encodeURIComponent(username)}`), {
      permisos,
    });
  }

  historial(filtro: FiltroHistorial) {
    let params = new HttpParams();
    for (const [clave, valor] of Object.entries(filtro)) {
      if (valor) params = params.set(clave, valor);
    }
    return this.http.get<Historial>(this.api.buildUrl('/v1/admin/auditoria'), { params });
  }

  sesiones() {
    return this.http.get<EventoSesion[]>(this.api.buildUrl('/v1/admin/auditoria/sesiones'));
  }
}
