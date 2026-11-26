import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { CobranzaListComponent } from './cobranza-list';
import { CobranzaService } from './cobranza-service';
import { CuentaPorCobrar } from './cobranza.model';
import { ClienteService } from '../../clientes/cliente/cliente-service';
import { AuthService } from '../../../core/auth/auth.service';

const cuenta: CuentaPorCobrar = {
  ventaId: 20, comprobante: 'F001-20', clienteId: 5, clienteNombre: 'Prueba',
  fechaVenta: '2026-01-01', fechaLimitePago: null, diasAtraso: 0, moneda: 'SOLES',
  total: 1180, cobrado: 0, saldo: 1180, tipoDeduccion: 'RETENCION', montoDeduccion: 35.4,
  deduccionPendiente: 35.4, netoPorCobrar: 1144.6,
};

describe('Retencion de pagos en Cobranza', () => {
  async function preparar(moneda: 'SOLES' | 'DOLARES' = 'SOLES') {
    const registrar = vi.fn().mockReturnValue(of({ id: 1, montoTotal: 15 }));
    const c = { ...cuenta, moneda };
    TestBed.configureTestingModule({ providers: [
      provideRouter([]),
      { provide: AuthService, useValue: { puede: () => true } },
      { provide: ClienteService, useValue: { listarTodos: () => of([]) } },
      { provide: CobranzaService, useValue: {
        porCobrar: () => of([c]), historial: () => of([]), devoluciones: () => of([]), registrar,
      } },
    ] });
    const fixture = TestBed.createComponent(CobranzaListComponent);
    await fixture.whenStable();
    const pagina = fixture.componentInstance;
    pagina.abrirCobro(c, true);
    await fixture.whenStable();
    return { fixture, pagina, registrar };
  }

  it('envia la base del pago parcial y su retencion, conservando el saldo restante', async () => {
    const { fixture, pagina, registrar } = await preparar();
    const input = fixture.nativeElement.querySelector('[formControlName="baseRetencion"]') as HTMLInputElement;
    input.value = '500'; input.dispatchEvent(new Event('input'));
    pagina.cobroForm.controls.referencia.setValue('RET-1');
    await fixture.whenStable();
    expect(pagina.abonos.at(0).controls.monto.value).toBe(15);
    expect((fixture.nativeElement.querySelector('[formControlName="monto"]') as HTMLInputElement).readOnly).toBe(true);
    pagina.confirmar();
    expect(registrar).toHaveBeenCalledWith(expect.objectContaining({
      metodoPago: 'RETENCION', abonos: [{ ventaId: 20, monto: 15, baseRetencion: 500 }],
    }));
    expect(pagina.mensaje()).not.toContain('quedaron pagadas');
  });

  it('no presenta un saldo completo como una retencion sin conocer el pago bruto', async () => {
    const { pagina, registrar } = await preparar();
    pagina.cobroForm.controls.referencia.setValue('RET-2');
    pagina.confirmar();
    expect(registrar).not.toHaveBeenCalled();
    expect(pagina.errorDialogo()).toContain('importe bruto');
    pagina.cobroForm.controls.metodoPago.setValue('TRANSFERENCIA');
    pagina.cambiarMetodo();
    expect(pagina.abonos.at(0).controls.monto.value).toBeNull();
  });

  it('incluye el cambio de la fecha del pago para retenciones en dolares', async () => {
    const { pagina, registrar } = await preparar('DOLARES');
    pagina.abonos.at(0).controls.baseRetencion.setValue(500);
    pagina.calcularRetencion(0);
    pagina.cobroForm.patchValue({ referencia: 'RET-USD', tipoCambioRetencion: 3.75 });
    pagina.confirmar();
    expect(registrar).toHaveBeenCalledWith(expect.objectContaining({ tipoCambioRetencion: 3.75, moneda: 'DOLARES' }));
  });
  it('ofrece retencion solamente cuando existe un documento historico con esa deduccion', async () => {
    const { pagina } = await preparar();
    expect(pagina.metodosCobro().some(m => m.valor === 'RETENCION')).toBe(true);
    const nueva = { ...cuenta, tipoDeduccion: 'NINGUNA' as const, montoDeduccion: null, deduccionPendiente: 0,
      netoPorCobrar: 1180 };
    pagina.cuentas.set([nueva]);
    pagina.abrirCobro(nueva);
    expect(pagina.metodosCobro().some(m => m.valor === 'RETENCION')).toBe(false);
  });
});
