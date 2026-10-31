import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { EventoSesion, FiltroHistorial, Historial, ResultadoCambio, SeguridadService } from '../services/seguridad-service';

/**
 * Configuración › Historial de cambios (solo ADMIN). Dos pestañas:
 *  - Cambios: toda acción que intentó modificar algo, con quién, cuándo, desde qué IP y cómo terminó.
 *    Incluye los intentos sin permiso (Denegado), que son los que más interesan por seguridad.
 *  - Inicios de sesión: entradas, contraseñas erradas y salidas que registra Keycloak (7 días).
 */
@Component({
  selector: 'app-historial',
  imports: [DatePipe, RouterLink],
  templateUrl: './historial.html',
  styleUrl: './historial.css',
})
export class HistorialComponent {
  private readonly seguridad = inject(SeguridadService);

  readonly pestana = signal<'CAMBIOS' | 'SESIONES'>('CAMBIOS');
  readonly filtro = signal<FiltroHistorial>({});
  readonly historial = signal<Historial | null>(null);
  readonly sesiones = signal<EventoSesion[] | null>(null);
  readonly cargando = signal(false);
  readonly error = signal<string | null>(null);
  /** Fila abierta para ver ruta, roles y detalle. */
  readonly abierto = signal<number | null>(null);

  readonly resultados: { valor: ResultadoCambio; etiqueta: string }[] = [
    { valor: 'EXITO', etiqueta: 'Hecho' },
    { valor: 'DENEGADO', etiqueta: 'Sin permiso' },
    { valor: 'RECHAZADO', etiqueta: 'Rechazado' },
    { valor: 'ERROR', etiqueta: 'Error' },
  ];

  readonly etiquetaResultado: Record<ResultadoCambio, string> = {
    EXITO: 'Hecho',
    DENEGADO: 'Sin permiso',
    RECHAZADO: 'Rechazado',
    ERROR: 'Error',
  };

  readonly etiquetaSesion: Record<EventoSesion['tipo'], string> = {
    LOGIN: 'Inició sesión',
    LOGIN_ERROR: 'Intento fallido',
    LOGOUT: 'Cerró sesión',
  };

  /** Resumen de lo que se ve: total e intentos denegados. */
  readonly denegados = computed(() => this.historial()?.registros.filter((r) => r.resultado === 'DENEGADO').length ?? 0);
  readonly fallidos = computed(() => this.sesiones()?.filter((s) => s.tipo === 'LOGIN_ERROR').length ?? 0);

  constructor() {
    this.buscar();
  }

  cambiarPestana(p: 'CAMBIOS' | 'SESIONES') {
    this.pestana.set(p);
    this.error.set(null);
    if (p === 'SESIONES' && this.sesiones() === null) this.cargarSesiones();
  }

  filtrar(campo: keyof FiltroHistorial, valor: string) {
    this.filtro.update((f) => ({ ...f, [campo]: valor }));
    this.buscar();
  }

  limpiar() {
    this.filtro.set({});
    this.buscar();
  }

  hayFiltro(): boolean {
    return Object.values(this.filtro()).some((v) => !!v);
  }

  buscar() {
    this.cargando.set(true);
    this.error.set(null);
    this.seguridad.historial(this.filtro()).subscribe({
      next: (h) => {
        this.historial.set(h);
        this.cargando.set(false);
      },
      error: (err) => {
        this.error.set(err.error?.message ?? 'No se pudo cargar el historial.');
        this.cargando.set(false);
      },
    });
  }

  cargarSesiones() {
    this.cargando.set(true);
    this.seguridad.sesiones().subscribe({
      next: (s) => {
        this.sesiones.set(s);
        this.cargando.set(false);
      },
      error: (err) => {
        this.error.set(err.error?.message ?? 'No se pudieron consultar los inicios de sesión en Keycloak.');
        this.cargando.set(false);
      },
    });
  }

  alternarFila(id: number) {
    this.abierto.update((a) => (a === id ? null : id));
  }
}
