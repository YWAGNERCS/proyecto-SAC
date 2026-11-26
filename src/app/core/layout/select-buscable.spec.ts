import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { SelectBuscableDirective } from './select-buscable';

@Component({
  imports: [SelectBuscableDirective, ReactiveFormsModule, FormsModule],
  template: `
    <label for="cliente">Cliente</label>
    <select id="cliente" appBuscable="Cliente" [formControl]="cliente" (change)="cambios = cambios + 1">
      <option [ngValue]="null">Todos los clientes</option>
      @for (c of clientes; track c.id) {
        <option [ngValue]="c.id" [attr.data-busqueda]="c.dni">{{ c.nombre }}</option>
      }
    </select>
    <select appBuscable="Proveedor" [(ngModel)]="proveedor">
      <option [ngValue]="null">Todos</option>
      <option [ngValue]="25">Proveedor de prueba</option>
    </select>
  `,
})
class FormularioPrueba {
  cliente = new FormControl<number | null>(null);
  proveedor: number | null = null;
  clientes = [
    ...Array.from({ length: 100 }, (_, i) => ({ id: i + 1, nombre: 'Cliente ' + i, dni: String(10000000 + i) })),
    { id: 101, nombre: 'José Pérez', dni: '87654321' },
  ];
  cambios = 0;
}

describe('Selector con búsqueda', () => {
  async function preparar() {
    const fixture = TestBed.createComponent(FormularioPrueba);
    await fixture.whenStable();
    fixture.detectChanges();
    const input = fixture.nativeElement.querySelector('#cliente-busqueda') as HTMLInputElement;
    return { fixture, input, formulario: fixture.componentInstance };
  }

  it('filtra cien clientes por documento y selecciona el ID con el teclado', async () => {
    const { fixture, input, formulario } = await preparar();
    input.dispatchEvent(new FocusEvent('focus'));
    input.value = '87654321';
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    const opciones = fixture.nativeElement.querySelectorAll('[role="option"]');
    expect(opciones.length).toBe(1);
    expect(opciones[0].textContent).toContain('José Pérez');
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', cancelable: true }));
    await fixture.whenStable();
    expect(formulario.cliente.value).toBe(101);
    expect(formulario.cambios).toBe(1);
    expect(formulario.cliente.touched).toBe(true);
    expect(input.value).toBe('José Pérez');
  });

  it('ignora tildes y restaura la selección al cancelar una búsqueda', async () => {
    const { fixture, input, formulario } = await preparar();
    formulario.cliente.setValue(101);
    await fixture.whenStable();
    input.dispatchEvent(new FocusEvent('focus'));
    input.value = 'jose perez';
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelectorAll('[role="option"]').length).toBe(1);
    input.value = 'inexistente';
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Sin coincidencias');
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', cancelable: true, bubbles: true }));
    await fixture.whenStable();
    expect(formulario.cliente.value).toBe(101);
    expect(input.value).toBe('José Pérez');
  });

  it('actualiza las opciones y respeta cambios de valor, reinicios y deshabilitado', async () => {
    const { fixture, input, formulario } = await preparar();
    formulario.clientes = [{ id: 102, nombre: 'Cliente recién cargado', dni: '12345678' }];
    fixture.detectChanges();
    await fixture.whenStable();
    formulario.cliente.setValue(102);
    await fixture.whenStable();
    expect(input.value).toBe('Cliente recién cargado');
    formulario.cliente.disable({ emitEvent: false });
    await fixture.whenStable();
    expect(input.disabled).toBe(true);
    formulario.cliente.enable();
    formulario.cliente.reset();
    await fixture.whenStable();
    expect(input.disabled).toBe(false);
    expect(input.value).toBe('Todos los clientes');
  });

  it('refleja opciones cargadas después, reinicios y campos deshabilitados', async () => {
    const { fixture, input, formulario } = await preparar();
    formulario.clientes = [{ id: 102, nombre: 'Cliente nuevo', dni: '11223344' }];
    formulario.cliente.setValue(102);
    await fixture.whenStable();
    expect(input.value).toBe('Cliente nuevo');
    formulario.cliente.disable();
    await fixture.whenStable();
    expect(input.disabled).toBe(true);
    formulario.cliente.enable();
    formulario.cliente.reset();
    await fixture.whenStable();
    expect(input.disabled).toBe(false);
    expect(input.value).toBe('Todos los clientes');
  });

  it('conserva el valor numérico y la opción null en filtros ngModel', async () => {
    const { fixture, formulario } = await preparar();
    const input = fixture.nativeElement.querySelectorAll('input')[1] as HTMLInputElement;
    input.dispatchEvent(new FocusEvent('focus'));
    await fixture.whenStable();
    const opciones = fixture.nativeElement.querySelectorAll('app-selector-buscable')[1].querySelectorAll('[role="option"]');
    opciones[1].click();
    await fixture.whenStable();
    expect(formulario.proveedor).toBe(25);
    input.dispatchEvent(new FocusEvent('focus'));
    await fixture.whenStable();
    fixture.nativeElement.querySelectorAll('app-selector-buscable')[1].querySelector('[role="option"]').click();
    await fixture.whenStable();
    expect(formulario.proveedor).toBe(null);
  });
});
