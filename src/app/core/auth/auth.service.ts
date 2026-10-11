import { Injectable, signal, computed } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Router } from '@angular/router';
import { environment } from '../../../environments/environment';
import { Observable, tap, catchError, throwError } from 'rxjs';

export interface UsuarioSesion {
  username: string;
  roles: string[];
  /** Header Base64 codificado, listo para adjuntar. */
  basicToken: string;
}

const STORAGE_KEY = 'sgejd_auth';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly meUrl = `${environment.apiBaseUrl}/v1/admin/auth/me`;

  /** Estado reactivo de la sesión. */
  private readonly _usuario = signal<UsuarioSesion | null>(this._leerStorage());

  /** Solo lectura para el resto de la app. */
  readonly usuario = this._usuario.asReadonly();

  /** true si hay sesión activa. */
  readonly estaLogueado = computed(() => this._usuario() !== null);

  constructor(private http: HttpClient, private router: Router) {}

  /**
   * Envía las credenciales al backend (HTTP Basic sobre /auth/me).
   * Si 200 → guarda sesión; si 401/403 → rechaza.
   */
  login(username: string, password: string): Observable<UsuarioSesion> {
    const basicToken = btoa(`${username}:${password}`);
    const headers = new HttpHeaders({ Authorization: `Basic ${basicToken}` });

    return this.http.get<{ username: string; roles: string[] }>(this.meUrl, { headers }).pipe(
      tap((datos) => {
        const sesion: UsuarioSesion = { username: datos.username, roles: datos.roles, basicToken };
        this._usuario.set(sesion);
        localStorage.setItem(STORAGE_KEY, JSON.stringify(sesion));
      }),
      // Proyectar al tipo que maneja la app
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      tap(() => {}) as any,
      catchError((err) => throwError(() => err)),
    ) as Observable<UsuarioSesion>;
  }

  logout(): void {
    this._usuario.set(null);
    localStorage.removeItem(STORAGE_KEY);
    this.router.navigate(['/login']);
  }

  /** Devuelve el token Basic para adjuntarlo en el interceptor. */
  getBasicToken(): string | null {
    return this._usuario()?.basicToken ?? null;
  }

  private _leerStorage(): UsuarioSesion | null {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      return raw ? (JSON.parse(raw) as UsuarioSesion) : null;
    } catch {
      return null;
    }
  }
}
