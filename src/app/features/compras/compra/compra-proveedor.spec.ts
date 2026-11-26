import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { CompraFormComponent } from './compra-form';
import { CompraService } from './compra-service';
import { ProveedorService } from '../../proveedores/proveedor/proveedor-service';
import { ProductoService } from '../../catalogo/producto/producto-service';
import { Proveedor } from '../../proveedores/proveedor/proveedor.model';

describe('Proveedor desde una compra', () => {
  const nuevo: Proveedor = {
    id: 25, tipo: 'EMPRESA', ruc: '20999999993', nombreORazonSocial: 'Proveedor de prueba',
  };

  async function preparar() {
    const crear = vi.fn().mockReturnValue(of(nuevo));
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: ProveedorService, useValue: { listarTodos: () => of([]), crear } },
        { provide: ProductoService, useValue: { listarTodos: () => of([]) } },
        { provide: CompraService, useValue: {} },
      ],
    });
    const fixture = TestBed.createComponent(CompraFormComponent);
    const compra = fixture.componentInstance;
    compra.form.controls.numeroFactura.setValue('F001-123');
    compra.nuevoDetalle.setValue({ productoId: 11, cantidad: 2, precioUnitarioCompra: 25 });
    compra.agregarDetalle();
    compra.creandoProveedor.set(true);
    await fixture.whenStable();
    const dialogo = fixture.nativeElement.querySelector('[role="dialog"]') as HTMLElement;
    return { fixture, compra, crear, dialogo };
  }

  function escribir(dialogo: HTMLElement, campo: string, valor: string) {
    const input = dialogo.querySelector('[formControlName="' + campo + '"]') as HTMLInputElement;
    input.value = valor;
    input.dispatchEvent(new Event('input'));
  }

  it('guarda, selecciona al proveedor y conserva las líneas y la factura de la compra', async () => {
    const { fixture, compra, crear, dialogo } = await preparar();
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate');
    escribir(dialogo, 'documento', nuevo.ruc!);
    escribir(dialogo, 'nombreORazonSocial', nuevo.nombreORazonSocial);
    dialogo.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await fixture.whenStable();

    expect(crear).toHaveBeenCalledOnce();
    expect(compra.form.controls.proveedorId.value).toBe(25);
    expect(compra.proveedores()).toEqual([nuevo]);
    expect(compra.creandoProveedor()).toBe(false);
    expect(compra.form.controls.numeroFactura.value).toBe('F001-123');
    expect(compra.detalles.getRawValue()).toEqual([{ productoId: 11, cantidad: 2, precioUnitarioCompra: 25 }]);
    expect(navegar).not.toHaveBeenCalled();
  });

  it('mantiene el diálogo abierto y conserva la compra cuando falla el registro', async () => {
    const { fixture, compra, crear, dialogo } = await preparar();
    crear.mockReturnValue(throwError(() => ({ status: 409, error: { message: 'RUC ya registrado' } })));
    escribir(dialogo, 'documento', nuevo.ruc!);
    escribir(dialogo, 'nombreORazonSocial', nuevo.nombreORazonSocial);
    dialogo.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await fixture.whenStable();
    expect(dialogo.textContent).toContain('RUC ya registrado');
    expect(compra.creandoProveedor()).toBe(true);
    expect(compra.form.controls.proveedorId.value).toBe(null);
    expect(compra.detalles.length).toBe(1);
  });

  it('permite cancelar y reutilizar un proveedor existente sin duplicarlo', async () => {
    const { fixture, compra, crear, dialogo } = await preparar();
    const cancelar = Array.from(dialogo.querySelectorAll('button')).find(b => b.textContent?.trim() === 'Cancelar')!;
    cancelar.click();
    await fixture.whenStable();
    expect(compra.creandoProveedor()).toBe(false);
    expect(compra.detalles.length).toBe(1);
    expect(crear).not.toHaveBeenCalled();

    compra.proveedores.set([nuevo]);
    compra.proveedorCreado(nuevo);
    expect(compra.proveedores()).toHaveLength(1);
    expect(compra.form.controls.proveedorId.value).toBe(25);
  });
});
