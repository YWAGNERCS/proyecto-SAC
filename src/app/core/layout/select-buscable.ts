import { AfterViewInit, ComponentRef, Directive, ElementRef, OnDestroy, ViewContainerRef, inject, input } from '@angular/core';
import { NgControl } from '@angular/forms';
import { Subscription } from 'rxjs';
import { SelectorBuscableComponent } from './selector-buscable';

let siguienteSelector = 0;

/**
 * Conserva el select de Angular como fuente de opciones y valores (incluidos ngValue y null).
 * La interfaz de búsqueda dispara su evento change, por lo que mantiene validadores y dependencias.
 */
@Directive({ selector: 'select[appBuscable]' })
export class SelectBuscableDirective implements AfterViewInit, OnDestroy {
  readonly appBuscable = input('Seleccionar');
  private readonly elemento = inject<ElementRef<HTMLSelectElement>>(ElementRef);
  private readonly contenedor = inject(ViewContainerRef);
  private readonly control = inject(NgControl, { self: true, optional: true });
  private componente?: ComponentRef<SelectorBuscableComponent>;
  private observador?: MutationObserver;
  private suscripcion?: Subscription;
  private quitarEtiqueta?: () => void;
  private readonly actualizar = () => this.sincronizar();

  ngAfterViewInit() {
    const select = this.elemento.nativeElement;
    this.componente = this.contenedor.createComponent(SelectorBuscableComponent);
    const instancia = this.componente.instance;
    const idCampo = (select.id || 'selector-' + ++siguienteSelector) + '-busqueda';
    this.componente.setInput('idCampo', idCampo);
    this.componente.setInput('etiqueta', this.appBuscable());
    this.componente.setInput('placeholder', 'Buscar ' + this.appBuscable().toLocaleLowerCase() + '…');
    instancia.seleccion.subscribe(valor => {
      select.value = valor;
      select.dispatchEvent(new Event('change', { bubbles: true }));
      this.sincronizar();
    });
    instancia.tocado.subscribe(() => select.dispatchEvent(new Event('blur')));
    // El label original sigue describiendo el campo aun cuando el select ya no se muestra.
    for (const label of Array.from(select.labels ?? [])) {
      const enfocar = (evento: Event) => {
        evento.preventDefault();
        instancia.campo.nativeElement.focus();
      };
      label.addEventListener('click', enfocar);
      const quitarAnterior = this.quitarEtiqueta;
      this.quitarEtiqueta = () => { quitarAnterior?.(); label.removeEventListener('click', enfocar); };
    }
    select.hidden = true;
    select.style.display = 'none';
    select.setAttribute('aria-hidden', 'true');
    this.observador = new MutationObserver(this.actualizar);
    this.observador.observe(select, {
      childList: true, subtree: true, characterData: true, attributes: true,
      attributeFilter: ['disabled', 'class', 'selected', 'value', 'data-busqueda', 'aria-describedby', 'required'],
    });
    this.suscripcion = this.control?.valueChanges?.subscribe(() => queueMicrotask(this.actualizar));
    select.addEventListener('change', this.actualizar);
    queueMicrotask(this.actualizar);
  }

  private sincronizar() {
    if (!this.componente || this.componente.hostView.destroyed) return;
    const select = this.elemento.nativeElement;
    this.componente.setInput('opciones', Array.from(select.options).map(o => ({
      valor: o.value,
      etiqueta: o.textContent?.trim() ?? '',
      busqueda: o.getAttribute('data-busqueda') ?? '',
      deshabilitada: o.disabled,
    })));
    this.componente.setInput('valor', select.value);
    this.componente.setInput('deshabilitado', select.disabled || select.hasAttribute('disabled'));
    this.componente.setInput('invalido', select.classList.contains('is-invalid'));
    this.componente.setInput('requerido', select.required);
    this.componente.setInput('descripcion', select.getAttribute('aria-describedby') ?? '');
  }

  ngOnDestroy() {
    this.observador?.disconnect();
    this.suscripcion?.unsubscribe();
    this.quitarEtiqueta?.();
    this.elemento.nativeElement.removeEventListener('change', this.actualizar);
    this.componente?.destroy();
    this.componente = undefined;
  }
}
