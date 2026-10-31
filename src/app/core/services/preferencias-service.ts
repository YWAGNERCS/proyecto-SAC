import { Injectable, computed, effect, signal } from '@angular/core';

/** Periodo con el que abre el tablero de Inicio. */
export type PeriodoTablero = 'MES' | 'MES_ANTERIOR' | 'TREINTA' | 'TRIMESTRE' | 'ANIO';
export type TamanoLetra = 'NORMAL' | 'GRANDE';
/** SISTEMA sigue al modo claro/oscuro de Windows o del celular. */
export type Tema = 'CLARO' | 'OSCURO' | 'SISTEMA';

const CLAVE = 'sgejd.preferencias';

interface Guardadas {
  periodoTablero?: PeriodoTablero;
  tamanoLetra?: TamanoLetra;
  tema?: Tema;
}

/**
 * Preferencias de quien usa esta computadora (Configuración): periodo inicial del tablero, tamaño
 * de letra y tema claro/oscuro. Son comodidades personales, por eso se guardan en el navegador (localStorage) y no en el
 * servidor; si el navegador no deja guardarlas, el sistema funciona igual con los valores por defecto.
 */
@Injectable({ providedIn: 'root' })
export class PreferenciasService {
  private readonly guardadas = this.leer();

  readonly periodoTablero = signal<PeriodoTablero>(this.guardadas.periodoTablero ?? 'MES');
  readonly tamanoLetra = signal<TamanoLetra>(this.guardadas.tamanoLetra ?? 'NORMAL');
  readonly tema = signal<Tema>(this.guardadas.tema ?? 'SISTEMA');

  private readonly consultaOscuro = matchMedia('(prefers-color-scheme: dark)');
  private readonly sistemaOscuro = signal(this.consultaOscuro.matches);
  /** El tema que se ve de verdad (SISTEMA ya resuelto). */
  readonly oscuro = computed(() => this.tema() === 'OSCURO' || (this.tema() === 'SISTEMA' && this.sistemaOscuro()));

  constructor() {
    // Si el tema es SISTEMA y el usuario cambia Windows a oscuro, la página lo sigue sin recargar
    this.consultaOscuro.addEventListener('change', (e) => this.sistemaOscuro.set(e.matches));
    effect(() => {
      // data-theme en <html>: styles.css redefine los colores con [data-theme='oscuro']
      document.documentElement.dataset['theme'] = this.oscuro() ? 'oscuro' : 'claro';
      document.documentElement.style.colorScheme = this.oscuro() ? 'dark' : 'light';
    });
    effect(() => {
      const datos: Guardadas = { periodoTablero: this.periodoTablero(), tamanoLetra: this.tamanoLetra(), tema: this.tema() };
      try {
        localStorage.setItem(CLAVE, JSON.stringify(datos));
      } catch {
        // Navegador en modo privado o con el almacenamiento bloqueado: se usan los valores en memoria
      }
      document.body.classList.toggle('letra-grande', this.tamanoLetra() === 'GRANDE');
    });
  }

  /** Botón sol/luna de la barra: pasa al tema contrario del que se ve (deja de seguir al sistema). */
  alternarTema() {
    this.tema.set(this.oscuro() ? 'CLARO' : 'OSCURO');
  }

  private leer(): Guardadas {
    try {
      return JSON.parse(localStorage.getItem(CLAVE) ?? '{}') as Guardadas;
    } catch {
      return {};
    }
  }
}
