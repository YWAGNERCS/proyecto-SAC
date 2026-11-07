import { Component, computed, input, output } from '@angular/core';
import { IconoComponent } from './icono';

/** Columna y dirección por la que se ordena un listado (null = el orden de siempre). */
export interface Orden {
  campo: string;
  dir: 'asc' | 'desc';
}

/** Cómo se lee el valor de cada columna ordenable de una fila. */
export type CamposOrden<T> = Record<string, (fila: T) => string | number | null | undefined>;

/** "cliente,asc": lo que espera el parámetro `orden` del backend (vacío = orden por defecto). */
export function ordenParaServidor(o: Orden | null): string {
  return o ? `${o.campo},${o.dir}` : '';
}

/**
 * Siguiente orden al hacer clic en una columna: primero ascendente (A→Z, de menor a mayor), luego
 * descendente y al tercer clic vuelve al orden de siempre. Las fechas y montos empiezan por lo más
 * reciente o lo más alto (`primero = 'desc'`), que es lo que se suele buscar.
 */
export function siguienteOrden(actual: Orden | null, campo: string, primero: 'asc' | 'desc' = 'asc'): Orden | null {
  if (actual?.campo !== campo) return { campo, dir: primero };
  if (actual.dir === primero) return { campo, dir: primero === 'asc' ? 'desc' : 'asc' };
  return null;
}

const TEXTO = new Intl.Collator('es-PE', { sensitivity: 'base', numeric: true });

/**
 * Ordena en el navegador (listados que ya llegan completos): texto alfabético en español sin distinguir
 * mayúsculas ni tildes, y con los números dentro del texto en orden natural ("F001-9" antes que "F001-10").
 * Los vacíos van al final en las dos direcciones; con orden null devuelve la lista tal como vino.
 */
export function ordenarLista<T>(lista: readonly T[], orden: Orden | null, campos: CamposOrden<T>): T[] {
  const leer = orden ? campos[orden.campo] : undefined;
  if (!orden || !leer) return [...lista];
  const signo = orden.dir === 'asc' ? 1 : -1;
  return lista
    .map((fila, i) => ({ fila, i, valor: leer(fila) }))
    .sort((a, b) => {
      const va = a.valor, vb = b.valor;
      const vacioA = va === null || va === undefined || va === '';
      const vacioB = vb === null || vb === undefined || vb === '';
      if (vacioA || vacioB) return vacioA === vacioB ? a.i - b.i : vacioA ? 1 : -1;
      const c = typeof va === 'number' && typeof vb === 'number' ? va - vb : TEXTO.compare(String(va), String(vb));
      return c !== 0 ? c * signo : a.i - b.i;   // empate: se respeta el orden original
    })
    .map((x) => x.fila);
}

/**
 * Encabezado de columna que ordena al hacer clic:
 * `<th><app-orden campo="cliente" [orden]="orden()" (cambiar)="ordenar($event)">Cliente</app-orden></th>`.
 * La flecha indica la dirección; la pantalla decide si ordena en el navegador o se lo pide al servidor.
 */
@Component({
  selector: 'app-orden',
  imports: [IconoComponent],
  template: `
    <button type="button" class="orden-columna" [class.activa]="dir()" (click)="clic()" [attr.title]="ayuda()">
      <ng-content />
      <app-icono [nombre]="dir() === 'asc' ? 'ordenAsc' : dir() === 'desc' ? 'ordenDesc' : 'ordenar'" [tamano]="12" />
    </button>
  `,
  styles: `
    :host { display: inline-block; }
    .orden-columna {
      all: unset; cursor: pointer; display: inline-flex; align-items: center; gap: 4px;
      font: inherit; color: inherit; text-transform: inherit; letter-spacing: inherit; border-radius: 4px;
    }
    .orden-columna:hover { color: var(--text-color, inherit); }
    .orden-columna:focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; }
    .orden-columna app-icono { opacity: 0.45; }
    .orden-columna.activa { color: var(--primary); font-weight: 600; }
    .orden-columna.activa app-icono { opacity: 1; }
  `,
})
export class OrdenComponent {
  readonly campo = input.required<string>();
  readonly orden = input<Orden | null>(null);
  /** Dirección del primer clic: 'desc' para fechas y montos (lo más reciente / lo más alto primero). */
  readonly primero = input<'asc' | 'desc'>('asc');
  readonly cambiar = output<Orden | null>();

  protected readonly dir = computed(() => (this.orden()?.campo === this.campo() ? this.orden()!.dir : null));
  protected readonly ayuda = computed(() => {
    const d = this.dir();
    if (!d) return 'Ordenar por esta columna';
    const actual = d === 'asc' ? 'Ascendente (A→Z, de menor a mayor)' : 'Descendente (Z→A, de mayor a menor)';
    return d === this.primero() ? `${actual}. Clic para invertir` : `${actual}. Clic para volver al orden normal`;
  });

  protected clic() {
    this.cambiar.emit(siguienteOrden(this.orden(), this.campo(), this.primero()));
  }
}
