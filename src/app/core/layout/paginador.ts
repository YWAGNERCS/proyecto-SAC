import { Component, computed, input, output } from '@angular/core';
import { IconoComponent } from './icono';

/**
 * Pie de un listado paginado: "21–40 de 224", anterior/siguiente y cuántos por página.
 * No guarda estado: la pantalla le pasa la página actual y recarga cuando cambia.
 */
@Component({
  selector: 'app-paginador',
  imports: [IconoComponent],
  template: `
    @if (total() > 0) {
      <nav class="paginador" aria-label="Páginas">
        <span class="muted">{{ desde() }}–{{ hasta() }} de {{ total() }}</span>
        <div class="controles">
          <label class="por-pagina">
            <span class="muted">Por página</span>
            <select class="form-select" [value]="tamano()" (change)="tamanoCambia.emit(+$any($event.target).value)">
              @for (t of tamanos; track t) { <option [value]="t">{{ t }}</option> }
            </select>
          </label>
          <button type="button" class="btn btn-sm btn-outline-secondary" [disabled]="pagina() === 0"
                  (click)="paginaCambia.emit(pagina() - 1)" aria-label="Página anterior">
            <app-icono nombre="anterior" [tamano]="14" /> Anterior
          </button>
          <span class="actual">Página {{ pagina() + 1 }} de {{ totalPaginas() }}</span>
          <button type="button" class="btn btn-sm btn-outline-secondary" [disabled]="pagina() + 1 >= totalPaginas()"
                  (click)="paginaCambia.emit(pagina() + 1)" aria-label="Página siguiente">
            Siguiente <app-icono nombre="siguiente" [tamano]="14" />
          </button>
        </div>
      </nav>
    }
  `,
  styles: `
    .paginador { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 10px; padding: 12px 18px; border-top: 1px solid var(--border-color); font-size: 0.85rem; }
    .controles { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
    .por-pagina { display: flex; align-items: center; gap: 6px; margin: 0; }
    .por-pagina .form-select { width: auto; height: 32px; padding: 0 28px 0 10px; font-size: 0.82rem; }
    .actual { min-width: 110px; text-align: center; }
  `,
})
export class PaginadorComponent {
  readonly pagina = input.required<number>();
  readonly tamano = input.required<number>();
  readonly total = input.required<number>();
  readonly totalPaginas = input.required<number>();
  readonly paginaCambia = output<number>();
  readonly tamanoCambia = output<number>();

  readonly tamanos = [20, 50, 100];
  readonly desde = computed(() => (this.total() === 0 ? 0 : this.pagina() * this.tamano() + 1));
  readonly hasta = computed(() => Math.min((this.pagina() + 1) * this.tamano(), this.total()));
}
