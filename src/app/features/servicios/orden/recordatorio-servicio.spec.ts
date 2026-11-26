import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { RecordatorioServicioComponent } from './recordatorio-servicio';
describe('Recordatorio después del servicio', () => {
  it('guarda frecuencia y anticipación y muestra el resultado de ambos destinatarios', async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const fixture = TestBed.createComponent(RecordatorioServicioComponent);
    fixture.componentRef.setInput('ordenId', 42);
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne((r) => r.url.endsWith('/ordenes/42/recordatorio')).flush(null);
    await fixture.whenStable();
    const c = fixture.componentInstance;
    c.activo = true;
    c.meses = 6;
    c.dias = 2;
    c.guardar();
    const put = http.expectOne((r) => r.method === 'PUT');
    expect(put.request.body).toEqual({ activo: true, frecuenciaMeses: 6, anticipacionDias: 2 });
    put.flush({
      activo: true,
      frecuenciaMeses: 6,
      anticipacionDias: 2,
      fechaSiguiente: '2027-05-26',
      fechaAviso: '2027-05-24',
      correoCliente: 'cliente@example.com',
      correoOficina: 'oficina@example.com',
      estadoCliente: 'ENVIADO',
      estadoOficina: 'FALLIDO',
      errorOficina: 'Prueba de fallo',
    });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('ENVIADO');
    expect(fixture.nativeElement.textContent).toContain('Prueba de fallo');
    c.confirmar = false;
    c.reintentar();
    http.expectNone((r) => r.method === 'POST');
    http.verify();
  });
});
