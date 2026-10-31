import { Injectable, computed, signal } from '@angular/core';
import { environment } from '../../../environments/environment';
import { generarCodeChallenge, generarCodeVerifier, generarState } from './pkce';
import { decodeJwtPayload } from './jwt';

/** Sesión emitida por Keycloak. */
interface SesionKeycloak {
  accessToken: string;
  refreshToken: string;
  idToken: string;
  /** Momento (ms) en que vence el access token. */
  expiraEn: number;
}

interface TokenResponse {
  access_token: string;
  refresh_token: string;
  id_token: string;
  expires_in: number;
}

/** Usuario autenticado, leído de los claims del token. */
export interface UsuarioSesion {
  username: string;
  nombre: string;
  email: string;
  /** Roles del negocio: ADMIN (Lisandro) y/o ASISTENTE (Lucy). */
  roles: string[];
}

const ROLES_SISTEMA = ['ADMIN', 'ASISTENTE'];

/**
 * Permisos que el dueño puede delegar a un asistente (Configuración › Usuarios y permisos).
 * Viajan en el token como roles PERM_<código>; ADMIN los tiene todos. Mismo catálogo que Permiso.java.
 */
export type Permiso =
  | 'ANULAR_VENTAS'
  | 'ANULAR_COBROS'
  | 'DEVOLUCIONES'
  | 'CATALOGO'
  | 'ELIMINAR'
  | 'SERVICIOS'
  | 'DESCUENTO_AMPLIADO';

/** Cómo se le dice a cada permiso en pantalla (el backend envía además la descripción larga). */
export const NOMBRE_PERMISO: Record<Permiso, string> = {
  ANULAR_VENTAS: 'Anular ventas',
  ANULAR_COBROS: 'Anular cobros',
  DEVOLUCIONES: 'Registrar devoluciones',
  CATALOGO: 'Editar el catálogo',
  ELIMINAR: 'Eliminar clientes y proveedores',
  SERVICIOS: 'Contratos, cancelar órdenes y dar de baja equipos',
  DESCUENTO_AMPLIADO: 'Descuento del administrador',
};
const CLAVE_CODE_VERIFIER = 'sgejd_pkce_code_verifier';
const CLAVE_STATE = 'sgejd_pkce_state';
const CLAVE_DESTINO = 'sgejd_pkce_destino';
const CLAVE_SESION = 'sgejd_sesion';

/**
 * Autenticación con Keycloak por Authorization Code + PKCE (S11). La SPA nunca ve la contraseña:
 * el usuario la escribe en la pantalla de Keycloak, que devuelve un código de un solo uso a
 * /auth/callback; ese código se canjea por los tokens con el code_verifier.
 *
 * Dónde vive el token: en memoria (signal privado) con una copia espejo en sessionStorage, para
 * sobrevivir un F5 en la misma pestaña. Nunca en localStorage (persistiría entre pestañas y
 * después de cerrar el navegador). El code_verifier también va a sessionStorage porque la
 * redirección a Keycloak recarga la SPA; se borra apenas se usa.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly issuer = environment.keycloak.issuer;
  private readonly sesion = signal<SesionKeycloak | null>(this.restaurarSesion());
  /** Un solo refresco a la vez: si varias peticiones reciben 401 juntas, comparten el mismo intento. */
  private refrescoEnCurso: Promise<boolean> | null = null;

  /** Hay sesión (el access token puede estar vencido: el interceptor lo renueva ante un 401). */
  readonly autenticado = computed(() => this.sesion() !== null);

  readonly roles = computed<string[]>(() => {
    const token = this.sesion()?.accessToken;
    return token ? (decodeJwtPayload(token)?.realm_access?.roles ?? []) : [];
  });

  readonly usuario = computed<UsuarioSesion | null>(() => {
    const token = this.sesion()?.accessToken;
    const payload = token ? decodeJwtPayload(token) : null;
    if (!payload) return null;
    return {
      username: payload.preferred_username ?? '',
      nombre: payload.name ?? payload.preferred_username ?? 'Usuario',
      email: payload.email ?? '',
      roles: (payload.realm_access?.roles ?? []).filter((r) => ROLES_SISTEMA.includes(r)),
    };
  });

  /** Solo el dueño anula ventas, modifica el catálogo y elimina clientes/proveedores (el backend lo vuelve a exigir). */
  readonly esAdmin = computed(() => this.roles().includes('ADMIN'));

  /** Permisos delegados que trae el token (sin el prefijo PERM_). */
  readonly permisos = computed<Permiso[]>(() =>
    this.roles().filter((r) => r.startsWith('PERM_')).map((r) => r.slice(5) as Permiso),
  );

  /**
   * ¿Puede hacer esta operación del dueño? ADMIN siempre; un asistente solo si se le delegó.
   * Solo decide qué botones mostrar: el backend vuelve a exigir lo mismo (403).
   */
  puede(permiso: Permiso): boolean {
    return this.esAdmin() || this.roles().includes('PERM_' + permiso);
  }

  tieneRol(...rolesPermitidos: string[]): boolean {
    return rolesPermitidos.some((rol) => this.roles().includes(rol));
  }

  accessToken(): string | null {
    return this.sesion()?.accessToken ?? null;
  }

  /** Redirige a la pantalla de login de Keycloak. `destino`: a dónde volver después. */
  async iniciarLogin(destino = location.pathname + location.search): Promise<void> {
    const verifier = generarCodeVerifier();
    const state = generarState();
    const challenge = await generarCodeChallenge(verifier);
    sessionStorage.setItem(CLAVE_CODE_VERIFIER, verifier);
    sessionStorage.setItem(CLAVE_STATE, state);
    sessionStorage.setItem(CLAVE_DESTINO, destino.startsWith('/auth/') ? '/' : destino);

    const parametros = new URLSearchParams({
      client_id: environment.keycloak.clientId,
      response_type: 'code',
      redirect_uri: environment.keycloak.redirectUri,
      scope: 'openid profile email',
      state,
      code_challenge: challenge,
      code_challenge_method: 'S256',
    });
    location.href = `${this.issuer}/protocol/openid-connect/auth?${parametros}`;
  }

  /** Canjea el código que devolvió Keycloak por los tokens (se llama desde /auth/callback). */
  async completarLogin(code: string, stateRecibido: string): Promise<void> {
    const stateEsperado = sessionStorage.getItem(CLAVE_STATE);
    const verifier = sessionStorage.getItem(CLAVE_CODE_VERIFIER);
    sessionStorage.removeItem(CLAVE_STATE);
    if (!verifier || stateRecibido !== stateEsperado) {
      sessionStorage.removeItem(CLAVE_CODE_VERIFIER);
      throw new Error('El state no coincide o falta el code_verifier: inicie sesión de nuevo.');
    }
    // fetch y no HttpClient: esta petición va a Keycloak y no debe pasar por el interceptor del backend
    const respuesta = await fetch(`${this.issuer}/protocol/openid-connect/token`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        grant_type: 'authorization_code',
        client_id: environment.keycloak.clientId,
        redirect_uri: environment.keycloak.redirectUri,
        code,
        code_verifier: verifier,
      }),
    });
    // El verifier es de un solo uso: si el canje falla, se reinicia el login con uno nuevo
    sessionStorage.removeItem(CLAVE_CODE_VERIFIER);
    if (!respuesta.ok) {
      throw new Error('Keycloak no aceptó el código de autorización.');
    }
    this.guardarSesion(await respuesta.json());
  }

  /** Ruta a la que el usuario quería ir antes de pasar por Keycloak (se consume una vez). */
  destinoPendiente(): string {
    const destino = sessionStorage.getItem(CLAVE_DESTINO) ?? '/';
    sessionStorage.removeItem(CLAVE_DESTINO);
    return destino;
  }

  /** Renueva el access token con el refresh token. false si la sesión de Keycloak ya no es válida. */
  refrescar(): Promise<boolean> {
    this.refrescoEnCurso ??= this.pedirRefresco().finally(() => (this.refrescoEnCurso = null));
    return this.refrescoEnCurso;
  }

  /** Cierra la sesión en la SPA y en Keycloak (RP-Initiated Logout). */
  cerrarSesion(): void {
    const idToken = this.sesion()?.idToken;
    this.limpiar();
    const parametros = new URLSearchParams({
      client_id: environment.keycloak.clientId,
      post_logout_redirect_uri: environment.keycloak.postLogoutRedirectUri,
      ...(idToken ? { id_token_hint: idToken } : {}),
    });
    location.href = `${this.issuer}/protocol/openid-connect/logout?${parametros}`;
  }

  // ── Interno ───────────────────────────────────────────────

  private async pedirRefresco(): Promise<boolean> {
    const refreshToken = this.sesion()?.refreshToken;
    if (!refreshToken) return false;
    try {
      const respuesta = await fetch(`${this.issuer}/protocol/openid-connect/token`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({
          grant_type: 'refresh_token',
          client_id: environment.keycloak.clientId,
          refresh_token: refreshToken,
        }),
      });
      if (!respuesta.ok) {
        this.limpiar();
        return false;
      }
      this.guardarSesion(await respuesta.json());
      return true;
    } catch {
      // Keycloak no responde: no se puede renovar, pero tampoco se sabe si la sesión venció
      return false;
    }
  }

  private guardarSesion(datos: TokenResponse): void {
    const sesion: SesionKeycloak = {
      accessToken: datos.access_token,
      refreshToken: datos.refresh_token,
      // Un refresco no siempre devuelve id_token: se conserva el anterior para el logout
      idToken: datos.id_token ?? this.sesion()?.idToken ?? '',
      expiraEn: Date.now() + datos.expires_in * 1000,
    };
    this.sesion.set(sesion);
    try {
      sessionStorage.setItem(CLAVE_SESION, JSON.stringify(sesion));
    } catch {
      /* sin almacenamiento: la sesión dura lo que la página */
    }
  }

  private limpiar(): void {
    this.sesion.set(null);
    try {
      sessionStorage.removeItem(CLAVE_SESION);
      localStorage.removeItem('sgejd_auth'); // sesión HTTP Basic de versiones anteriores
    } catch {
      /* nada que limpiar */
    }
  }

  /** Se ejecuta una sola vez al crear el servicio: recupera la sesión tras un F5. */
  private restaurarSesion(): SesionKeycloak | null {
    try {
      const guardada = sessionStorage.getItem(CLAVE_SESION);
      if (!guardada) return null;
      const sesion = JSON.parse(guardada) as SesionKeycloak;
      // Aunque el access token haya vencido, el refresh token puede seguir válido: el interceptor lo renueva
      return sesion.refreshToken ? sesion : null;
    } catch {
      // JSON inválido (alguien editó sessionStorage): se descarta como una sesión vencida
      return null;
    }
  }
}
