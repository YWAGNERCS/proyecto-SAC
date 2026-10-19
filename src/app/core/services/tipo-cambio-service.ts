import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiService } from './api-service';

export type Moneda = 'SOLES' | 'DOLARES';

/** Refleja TipoCambioService.TipoCambio del backend (soles por dólar). */
export interface TipoCambio {
  fecha: string;
  compra: number;
  venta: number;
  origen: string;
}

@Injectable({ providedIn: 'root' })
export class TipoCambioService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiService);

  /** Tipo de cambio SUNAT de hoy (503 si SUNAT no responde: entonces se escribe a mano). */
  hoy(): Observable<TipoCambio> {
    return this.http.get<TipoCambio>(this.api.buildUrl('/v1/admin/tipo-cambio'));
  }
}

/**
 * Convierte un monto entre monedas con el tipo de cambio (soles por dólar), redondeando a 2 decimales.
 * Misma fórmula que TipoCambioService.convertir() del backend, para que la vista previa coincida.
 * Devuelve null si hace falta convertir y no hay tipo de cambio.
 */
export function convertir(monto: number, origen: Moneda, destino: Moneda, tipoCambio: number | null): number | null {
  if (origen === destino) return monto;
  if (!tipoCambio || tipoCambio <= 0) return null;
  const valor = origen === 'DOLARES' ? monto * tipoCambio : monto / tipoCambio;
  return Math.round(valor * 100) / 100;
}

export function simboloMoneda(moneda: Moneda | string | undefined | null): string {
  return moneda === 'DOLARES' ? 'US$' : 'S/';
}
