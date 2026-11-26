import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { CierreMensualComponent } from './cierre-mensual';
import { DescargaService } from '../../../core/services/descarga-service';
import { of } from 'rxjs';
import { vi } from 'vitest';
describe('Cierre mensual', () => {
  it('consulta el periodo y exporta el mismo mes mostrado aunque cambie el selector', async () => {
    const descargar = vi.fn(() => of(undefined));
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: DescargaService, useValue: { descargar } },
      ],
    });
    const fixture = TestBed.createComponent(CierreMensualComponent);
    const http = TestBed.inject(HttpTestingController);
    const req = http.expectOne((r) => r.url.endsWith('/reportes/cierre-mensual'));
    req.flush({ periodo: '2026-11', resumen: [], movimientos: [], observaciones: [] });
    await fixture.whenStable();
    fixture.componentInstance.periodo = '2026-12';
    fixture.componentInstance.exportar();
    expect(descargar).toHaveBeenCalledWith(
      '/v1/admin/reportes/cierre-mensual/exportar',
      'cierre_mensual_2026-11.xlsx',
      { anio: '2026', mes: '11' },
    );
    expect(fixture.nativeElement.textContent).toContain('No es una declaración');
    http.verify();
  });
});
