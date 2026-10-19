import { Injectable, computed, signal } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, map, switchMap, tap } from 'rxjs';
import { environment } from '../../../environments/environment';

/** Sesión con tokens emitidos por Keycloak (realm bomerp). */
export interface UsuarioSesion {
  username: string;
  nombre: string;
  email: string;
  /** Roles del negocio: ADMIN (Lisandro) y/o ASISTENTE (Lucy). */
  roles: string[];
  accessToken: string;
  refreshToken: string;
  /** Momento (ms) en que vence el access token. */
  expiraEn: number;
}

interface RespuestaToken {
  access_token: string;
  refresh_token: string;
  expires_in: number;
}

interface Perfil {
  username: string;
  nombre: string;
  email: string;
  roles: string[];
}

// sessionStorage: la sesión muere al cerrar el navegador (antes localStorage guardaba la contraseña en Base64)
const STORAGE_KEY = 'sgejd_sesion';
/** Se renueva el token un minuto antes de que venza. */
const MARGEN_RENOVACION_MS = 60_000;

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly kc = `${environment.keycloak.url}/realms/${environment.keycloak.realm}/protocol/openid-connect`;
  private readonly perfilUrl = `${environment.apiBaseUrl}/v1/auth/perfil`;

  private readonly _usuario = signal<UsuarioSesion | null>(this.leerStorage());
  readonly usuario = this._usuario.asReadonly();
  readonly estaLogueado = computed(() => this._usuario() !== null);
  /** Solo el dueño anula ventas, modifica el catálogo y elimina clientes/proveedores (el backend lo vuelve a exigir). */
  readonly esAdmin = computed(() => this._usuario()?.roles.includes('ADMIN') ?? false);

  private temporizador: ReturnType<typeof setTimeout> | null = null;

  constructor(private http: HttpClient, private router: Router) {
    const sesion = this._usuario();
    if (sesion) {
      if (sesion.expiraEn <= Date.now()) this.renovar();
      else this.programarRenovacion(sesion);
    }
  }

  /**
   * Pide el token a Keycloak con usuario y contraseña (Direct access grant: atajo de laboratorio
   * de S10; en S11 se reemplaza por Authorization Code + PKCE con redirección a Keycloak)
   * y luego lee nombre, correo y roles de GET /api/v1/auth/perfil.
   */
  login(username: string, password: string): Observable<UsuarioSesion> {
    const cuerpo = new HttpParams()
      .set('grant_type', 'password')
      .set('client_id', environment.keycloak.clientId)
      .set('username', username)
      .set('password', password);
    return this.pedirToken(cuerpo).pipe(switchMap((token) => this.completarSesion(token)));
  }

  /** Cierra la sesión también en Keycloak (invalida el refresh token). */
  logout(motivo?: string): void {
    const sesion = this._usuario();
    if (sesion) {
      const cuerpo = new HttpParams()
        .set('client_id', environment.keycloak.clientId)
        .set('refresh_token', sesion.refreshToken);
      this.http.post(`${this.kc}/logout`, cuerpo.toString(), { headers: this.formulario() })
        .subscribe({ error: () => undefined });
    }
    this.limpiar();
    this.router.navigate(['/login'], motivo ? { queryParams: { motivo } } : {});
  }

  getAccessToken(): string | null {
    return this._usuario()?.accessToken ?? null;
  }

  // ── Interno ───────────────────────────────────────────────

  private pedirToken(cuerpo: HttpParams): Observable<RespuestaToken> {
    return this.http.post<RespuestaToken>(`${this.kc}/token`, cuerpo.toString(), { headers: this.formulario() });
  }

  private completarSesion(token: RespuestaToken): Observable<UsuarioSesion> {
    const headers = new HttpHeaders({ Authorization: `Bearer ${token.access_token}` });
    return this.http.get<Perfil>(this.perfilUrl, { headers }).pipe(
      map((p) => ({
        username: p.username,
        nombre: p.nombre,
        email: p.email,
        roles: p.roles,
        accessToken: token.access_token,
        refreshToken: token.refresh_token,
        expiraEn: Date.now() + token.expires_in * 1000,
      })),
      tap((sesion) => this.guardar(sesion)),
    );
  }

  /** Cambia el refresh token por un access token nuevo, sin volver a pedir la contraseña. */
  private renovar(): void {
    const sesion = this._usuario();
    if (!sesion) return;
    const cuerpo = new HttpParams()
      .set('grant_type', 'refresh_token')
      .set('client_id', environment.keycloak.clientId)
      .set('refresh_token', sesion.refreshToken);
    this.pedirToken(cuerpo).subscribe({
      next: (token) =>
        this.guardar({
          ...sesion,
          accessToken: token.access_token,
          refreshToken: token.refresh_token,
          expiraEn: Date.now() + token.expires_in * 1000,
        }),
      // La sesión de Keycloak ya venció o se cerró: hay que volver a entrar
      error: () => {
        this.limpiar();
        this.router.navigate(['/login'], { queryParams: { motivo: 'expirada' } });
      },
    });
  }

  private programarRenovacion(sesion: UsuarioSesion): void {
    if (this.temporizador) clearTimeout(this.temporizador);
    const espera = Math.max(5_000, sesion.expiraEn - Date.now() - MARGEN_RENOVACION_MS);
    this.temporizador = setTimeout(() => this.renovar(), espera);
  }

  private guardar(sesion: UsuarioSesion): void {
    this._usuario.set(sesion);
    try {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(sesion));
    } catch {
      /* sin almacenamiento: la sesión dura lo que la pestaña */
    }
    this.programarRenovacion(sesion);
  }

  private limpiar(): void {
    if (this.temporizador) clearTimeout(this.temporizador);
    this.temporizador = null;
    this._usuario.set(null);
    try {
      sessionStorage.removeItem(STORAGE_KEY);
      localStorage.removeItem('sgejd_auth'); // sesión HTTP Basic de versiones anteriores
    } catch {
      /* nada que limpiar */
    }
  }

  private formulario(): HttpHeaders {
    return new HttpHeaders({ 'Content-Type': 'application/x-www-form-urlencoded' });
  }

  private leerStorage(): UsuarioSesion | null {
    try {
      const raw = sessionStorage.getItem(STORAGE_KEY);
      return raw ? (JSON.parse(raw) as UsuarioSesion) : null;
    } catch {
      return null;
    }
  }
}
