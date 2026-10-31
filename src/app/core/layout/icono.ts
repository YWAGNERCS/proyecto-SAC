import { Component, computed, input } from '@angular/core';
import { ICONOS } from './menu';

export type NombreIcono = keyof typeof ICONOS;

/**
 * Ícono de línea del sistema (los mismos del menú): <app-icono nombre="descargar" />.
 * Toma el color del texto que lo rodea; es decorativo (aria-hidden), así que el botón debe decir qué hace.
 */
@Component({
  selector: 'app-icono',
  template: `<svg viewBox="0 0 24 24" aria-hidden="true" [attr.width]="tamano()" [attr.height]="tamano()"><path [attr.d]="trazo()" /></svg>`,
  styles: `
    :host { display: inline-flex; vertical-align: -0.15em; flex: none; }
    svg { fill: none; stroke: currentColor; stroke-width: 2; stroke-linecap: round; stroke-linejoin: round; }
  `,
})
export class IconoComponent {
  readonly nombre = input.required<NombreIcono>();
  readonly tamano = input(16);
  protected readonly trazo = computed(() => ICONOS[this.nombre()]);
}
