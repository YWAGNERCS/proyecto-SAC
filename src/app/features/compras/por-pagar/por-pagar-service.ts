import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { ApiService } from '../../../core/services/api-service';

export type MetodoPagoProveedor = 'TRANSFERENCIA' | 'DEPOSITO' | 'EFECTIVO' | 'CHEQUE' | 'YAPE_PLIN';

export interface CuentaPorPagar {
  compraId: number;
  numeroFactura: string | null;
  proveedorId: number;
  proveedor: string;
  proveedorTelefono: string | null;
  fechaCompra: string;
  fechaVencimiento: string | null;
  moneda: 'SOLES' | 'DOLARES';
  total: number;
  pagado: number;
  saldo: number;
  /** Mayor que 0 si ya venció. */
  diasAtraso: number;
}

export interface ResumenPorPagar {
  moneda: 'SOLES' | 'DOLARES';
  porPagar: number;
  vencido: number;
  venceEnSieteDias: number;
  compras: number;
}

export interface PagoProveedor {
  id: number;
  compraId: number;
  numeroFactura: string | null;
  proveedorId: number;
  proveedor: string;
  fechaPago: string;
  monto: number;
  moneda: 'SOLES' | 'DOLARES';
  metodo: MetodoPagoProveedor;
  numeroOperacion: string | null;
  observacion: string | null;
  estado: 'VIGENTE' | 'ANULADO';
  fechaAnulacion: string | null;
  motivoAnulacion: string | null;
  anuladoPor: string | null;
  registradoPor: string | null;
}

export interface PagoRequest {
  compraId: number;
  fechaPago: string;
  monto: number;
  metodo: MetodoPagoProveedor;
  numeroOperacion: string | null;
  observacion: string | null;
}

/** Cuentas por pagar a proveedores (compras a crédito) y sus pagos. */
@Injectable({ providedIn: 'root' })
export class PorPagarService {
  private api = inject(ApiService);
  private http = inject(HttpClient);
  private base = '/v1/admin/cuentas-por-pagar';

  pendientes(proveedorId: number | null, soloVencidas: boolean) {
    let params = new HttpParams().set('soloVencidas', soloVencidas);
    if (proveedorId) params = params.set('proveedorId', proveedorId);
    return this.http.get<{ cuentas: CuentaPorPagar[]; resumen: ResumenPorPagar[] }>(this.api.buildUrl(this.base), { params });
  }

  pagos(filtro: { proveedorId?: number | null; desde?: string; hasta?: string }) {
    let params = new HttpParams();
    if (filtro.proveedorId) params = params.set('proveedorId', filtro.proveedorId);
    if (filtro.desde) params = params.set('desde', filtro.desde);
    if (filtro.hasta) params = params.set('hasta', filtro.hasta);
    return this.http.get<PagoProveedor[]>(this.api.buildUrl(`${this.base}/pagos`), { params });
  }

  registrar(pago: PagoRequest) {
    return this.http.post<PagoProveedor>(this.api.buildUrl(`${this.base}/pagos`), pago);
  }

  anular(id: number, motivo: string) {
    return this.http.post<PagoProveedor>(this.api.buildUrl(`${this.base}/pagos/${id}/anular`), { motivo });
  }
}
