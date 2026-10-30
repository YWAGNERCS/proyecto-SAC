import { Injectable, effect, signal } from '@angular/core';

/** Periodo con el que abre el tablero de Inicio. */
export type PeriodoTablero = 'MES' | 'MES_ANTERIOR' | 'TREINTA' | 'TRIMESTRE' | 'ANIO';
export type TamanoLetra = 'NORMAL' | 'GRANDE';

const CLAVE = 'sgejd.preferencias';

interface Guardadas {
  periodoTablero?: PeriodoTablero;
  tamanoLetra?: TamanoLetra;
}

/**
 * Preferencias de quien usa esta computadora (Configuración): periodo inicial del tablero y tamaño
 * de letra. Son comodidades personales, por eso se guardan en el navegador (localStorage) y no en el
 * servidor; si el navegador no deja guardarlas, el sistema funciona igual con los valores por defecto.
 */
@Injectable({ providedIn: 'root' })
export class PreferenciasService {
  private readonly guardadas = this.leer();

  readonly periodoTablero = signal<PeriodoTablero>(this.guardadas.periodoTablero ?? 'MES');
  readonly tamanoLetra = signal<TamanoLetra>(this.guardadas.tamanoLetra ?? 'NORMAL');

  constructor() {
    effect(() => {
      const datos: Guardadas = { periodoTablero: this.periodoTablero(), tamanoLetra: this.tamanoLetra() };
      try {
        localStorage.setItem(CLAVE, JSON.stringify(datos));
      } catch {
        // Navegador en modo privado o con el almacenamiento bloqueado: se usan los valores en memoria
      }
      document.body.classList.toggle('letra-grande', this.tamanoLetra() === 'GRANDE');
    });
  }

  private leer(): Guardadas {
    try {
      return JSON.parse(localStorage.getItem(CLAVE) ?? '{}') as Guardadas;
    } catch {
      return {};
    }
  }
}
