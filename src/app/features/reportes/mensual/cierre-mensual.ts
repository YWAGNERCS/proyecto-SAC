import { Component, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpParams } from '@angular/common/http';
import { ApiService } from '../../../core/services/api-service';
import { DescargaService } from '../../../core/services/descarga-service';

export interface ReporteMensual {
  periodo: string;
  resumen: {
    moneda: string;
    ventasVigentes: number;
    compras: number;
    igvVentasVigentes: number;
    igvComprasRegistradas: number;
    documentosValidos: number;
    notasValidas: number;
    igvDocumentosNeto: number;
    diferenciaIgvReferencial: number;
    diferenciaIgvOperativa: number;
  }[];
  movimientos: {
    tipo: string;
    id: number;
    fecha: string;
    numero: string | null;
    tercero: string | null;
    moneda: string;
    estado: string;
    ambiente: string | null;
    base: number;
    igv: number;
    total: number;
    incluido: boolean;
  }[];
  observaciones: string[];
}
@Component({
  selector: 'app-cierre-mensual',
  imports: [FormsModule, DecimalPipe],
  templateUrl: './cierre-mensual.html',
})
export class CierreMensualComponent {
  private http = inject(HttpClient);
  private api = inject(ApiService);
  private descarga = inject(DescargaService);
  periodo = new Date().getFullYear() + '-' + String(new Date().getMonth() + 1).padStart(2, '0');
  datos = signal<ReporteMensual | null>(null);
  error = signal('');
  cargando = signal(false);
  exportando = signal(false);
  constructor() {
    this.consultar();
  }
  private parametros() {
    const [a, m] = this.periodo.split('-').map(Number);
    return new HttpParams().set('anio', a).set('mes', m);
  }
  consultar() {
    if (!/^20\d{2}-(0[1-9]|1[0-2])$/.test(this.periodo)) {
      this.error.set('Seleccione un mes válido.');
      return;
    }
    this.cargando.set(true);
    this.error.set('');
    this.datos.set(null);
    this.http
      .get<ReporteMensual>(this.api.buildUrl('/v1/admin/reportes/cierre-mensual'), {
        params: this.parametros(),
      })
      .subscribe({
        next: (r) => {
          this.datos.set(r);
          this.cargando.set(false);
        },
        error: (e) => {
          this.error.set(e.error?.message ?? 'No se pudo consultar el cierre mensual.');
          this.cargando.set(false);
        },
      });
  }
  exportar() {
    const r = this.datos();
    if (!r) return;
    this.exportando.set(true);
    const [a, m] = r.periodo.split('-');
    const params = new HttpParams().set('anio', a).set('mes', m);
    this.descarga
      .descargar('/v1/admin/reportes/cierre-mensual/exportar', `cierre_mensual_${r.periodo}.xlsx`, {
        anio: a,
        mes: m,
      })
      .subscribe({
        next: () => this.exportando.set(false),
        error: () => {
          this.exportando.set(false);
          this.error.set('No se pudo descargar el reporte.');
        },
      });
  }
}
