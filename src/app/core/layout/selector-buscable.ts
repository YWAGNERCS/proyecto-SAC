import { Component, DestroyRef, ElementRef, ViewChild, computed, inject, input, output, signal } from '@angular/core';

export interface OpcionBuscable {
  valor: string;
  etiqueta: string;
  busqueda: string;
  deshabilitada: boolean;
}

/** Presentación del selector. La directiva conserva el control y los valores del formulario original. */
@Component({
  selector: 'app-selector-buscable',
  template: `
    <div class="selector-buscable">
      <input #campo type="text" class="form-control"
        [class.is-invalid]="invalido()" [id]="idCampo()" role="combobox"
        autocomplete="off" aria-autocomplete="list" aria-haspopup="listbox"
        [attr.aria-label]="etiqueta()" [attr.aria-expanded]="abierto()"
        [attr.aria-controls]="idCampo() + '-lista'"
        [attr.aria-activedescendant]="abierto() && resultados()[activo()] ? idCampo() + '-opcion-' + activo() : null"
        [attr.aria-invalid]="invalido() ? 'true' : null" [attr.aria-describedby]="descripcion() || null"
        [attr.aria-required]="requerido() ? 'true' : null" [disabled]="deshabilitado()"
        [placeholder]="placeholder()" [value]="abierto() ? consulta() : seleccionada()?.etiqueta ?? ''"
        (focus)="abrir()" (input)="buscar($event)" (blur)="cerrar()" (keydown)="tecla($event)">
      @if (abierto()) {
        <div #menu class="selector-buscable-menu" popover="manual">
          <div role="listbox" [id]="idCampo() + '-lista'" [attr.aria-label]="etiqueta()">
            @for (opcion of resultados(); track opcion.valor; let i = $index) {
              <div role="option" class="selector-buscable-opcion"
                [class.activa]="activo() === i" [id]="idCampo() + '-opcion-' + i"
                [attr.aria-selected]="opcion.valor === valor()"
                (mousedown)="$event.preventDefault()" (click)="elegir(opcion)">
                {{ opcion.etiqueta }}
              </div>
            } @empty {
              <div class="selector-buscable-vacio" role="status">Sin coincidencias</div>
            }
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    :host { display: block; min-width: 0; flex: 1; }
    .selector-buscable { position: relative; width: 100%; }
    input { width: 100%; }
    .selector-buscable-menu {
      position: fixed; z-index: 1100; inset: auto; margin: 0; padding: 0;
      max-height: 260px; overflow-y: auto; background: var(--surface, #fff);
      color: var(--text-main, #182c40); border: 1px solid var(--border-color, #b8ceda);
      box-sizing: border-box;
      border-radius: 10px; box-shadow: 0 8px 24px #0002;
    }
    .selector-buscable-opcion { padding: 10px 12px; cursor: pointer; overflow-wrap: anywhere; }
    .selector-buscable-opcion:hover, .selector-buscable-opcion.activa {
      background: var(--surface-muted, #e9f4f8);
    }
    .selector-buscable-vacio { padding: 12px; color: var(--text-muted, #64748b); }
  `],
})
export class SelectorBuscableComponent {
  private readonly documento = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement.ownerDocument;
  private panel?: HTMLElement;
  readonly opciones = input<OpcionBuscable[]>([]);
  readonly valor = input('');
  readonly etiqueta = input('Seleccionar');
  readonly placeholder = input('Escriba para buscar…');
  readonly idCampo = input('');
  readonly deshabilitado = input(false);
  readonly invalido = input(false);
  readonly requerido = input(false);
  readonly descripcion = input('');
  readonly seleccion = output<string>();
  readonly tocado = output<void>();
  readonly abierto = signal(false);
  readonly consulta = signal('');
  readonly activo = signal(0);
  readonly seleccionada = computed(() => this.opciones().find(o => o.valor === this.valor()));
  readonly resultados = computed(() => {
    const palabras = this.normalizar(this.consulta()).split(/\s+/).filter(Boolean);
    return this.opciones().filter(o => !o.deshabilitada &&
      palabras.every(palabra => this.normalizar(o.etiqueta + ' ' + o.busqueda).includes(palabra)));
  });
  @ViewChild('campo', { static: true }) campo!: ElementRef<HTMLInputElement>;
  @ViewChild('menu') set menu(elemento: ElementRef<HTMLElement> | undefined) {
    this.panel = elemento?.nativeElement;
    if (this.panel) {
      this.ubicar();
      this.panel.showPopover?.();
    }
  }

  constructor() {
    const ventana = this.documento.defaultView;
    this.documento.addEventListener('scroll', this.ubicar, true);
    ventana?.addEventListener('resize', this.ubicar);
    inject(DestroyRef).onDestroy(() => {
      this.documento.removeEventListener('scroll', this.ubicar, true);
      ventana?.removeEventListener('resize', this.ubicar);
    });
  }

  // La capa popover evita que las tarjetas y los diálogos recorten las coincidencias.
  private readonly ubicar = () => {
    const panel = this.panel;
    const ventana = this.documento.defaultView;
    if (!panel || !ventana) return;
    const campo = this.campo.nativeElement.getBoundingClientRect();
    const debajo = ventana.innerHeight - campo.bottom - 12;
    const arriba = campo.top - 12;
    const abrirDebajo = debajo >= 180 || debajo >= arriba;
    panel.style.left = Math.max(8, campo.left) + 'px';
    panel.style.width = Math.min(campo.width, ventana.innerWidth - 16) + 'px';
    panel.style.maxHeight = Math.max(60, Math.min(260, abrirDebajo ? debajo : arriba)) + 'px';
    panel.style.top = abrirDebajo ? campo.bottom + 4 + 'px' : 'auto';
    panel.style.bottom = abrirDebajo ? 'auto' : ventana.innerHeight - campo.top + 4 + 'px';
  };

  abrir() {
    if (this.deshabilitado()) return;
    this.consulta.set('');
    this.activo.set(0);
    this.abierto.set(true);
  }

  buscar(evento: Event) {
    this.consulta.set((evento.target as HTMLInputElement).value);
    this.activo.set(0);
    this.abierto.set(true);
  }

  cerrar() {
    this.abierto.set(false);
    this.consulta.set('');
    this.tocado.emit();
  }

  elegir(opcion: OpcionBuscable) {
    this.seleccion.emit(opcion.valor);
    this.cerrar();
  }

  tecla(evento: KeyboardEvent) {
    if (evento.key === 'Escape' && this.abierto()) {
      evento.preventDefault();
      evento.stopPropagation();
      this.cerrar();
    } else if (evento.key === 'ArrowDown' || evento.key === 'ArrowUp') {
      evento.preventDefault();
      if (!this.abierto()) this.abrir();
      else {
        const cambio = evento.key === 'ArrowDown' ? 1 : -1;
        this.activo.set(Math.max(0, Math.min(this.resultados().length - 1, this.activo() + cambio)));
      }
      this.campo.nativeElement.ownerDocument.getElementById(
        this.idCampo() + '-opcion-' + this.activo())?.scrollIntoView?.({ block: 'nearest' });
    } else if (evento.key === 'Enter' && this.abierto()) {
      evento.preventDefault();
      const opcion = this.resultados()[this.activo()];
      if (opcion) this.elegir(opcion);
    }
  }

  private normalizar(texto: string) {
    return texto.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase();
  }
}
