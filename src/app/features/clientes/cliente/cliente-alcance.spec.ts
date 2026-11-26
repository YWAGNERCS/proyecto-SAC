import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { ClienteFormComponent } from './cliente-form';
import { ClienteService } from './cliente-service';

describe('Clientes sin opcion de retencion en la entrega', () => {
  it('conserva la consulta de RUC y guarda sin exponer ni enviar la opcion de retencion', async () => {
    const datos = { tipo: 'EMPRESA', ruc: '20000000001', nombreORazonSocial: 'Cliente de prueba',
      direccion: 'Lima', agenteRetencion: true };
    const crear = vi.fn().mockReturnValue(of({ ...datos, id: 1 }));
    const consultarRetencion = vi.fn(() => { throw new Error('No se debe consultar el padron'); });
    TestBed.configureTestingModule({ providers: [provideRouter([]),
      { provide: ClienteService, useValue: { autocompletarRuc: () => of(datos), crear, consultarRetencion } },
    ] });
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(ClienteFormComponent);
    await fixture.whenStable();
    const raiz = fixture.nativeElement as HTMLElement;
    const doc = raiz.querySelector('#documento') as HTMLInputElement;
    doc.value = datos.ruc; doc.dispatchEvent(new Event('input'));
    fixture.componentInstance.buscarDocumento();
    await fixture.whenStable();
    expect((raiz.querySelector('#nombre') as HTMLInputElement).value).toBe('Cliente de prueba');
    expect(raiz.querySelector('[formControlName="agenteRetencion"]')).toBeNull();
    expect(raiz.textContent).not.toContain('Verificar retención');
    fixture.componentInstance.guardar();
    expect(crear).toHaveBeenCalledOnce();
    expect(crear.mock.calls[0][0]).not.toHaveProperty('agenteRetencion');
    expect(consultarRetencion).not.toHaveBeenCalled();
  });
});
