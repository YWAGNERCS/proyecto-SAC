import { Component, computed, input, model, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { IconoComponent } from '../../../core/layout/icono';

/**
 * Rutina de mantenimiento que se programa con la venta de un equipo de climatización o refrigeración.
 * Viaja al backend tal cual (Ventas la valida y Postventa programa las visitas).
 */
export interface RutinaMantenimiento {
  frecuenciaMeses: number;
  visitas: number;
  /** yyyy-MM-dd; null = la fecha de la venta más la frecuencia. */
  primeraVisita: string | null;
}

export const FRECUENCIAS = [1, 2, 3, 4, 6, 12];
const MAX_VISITAS = 24;

/** Hoy en formato yyyy-MM-dd (hora local de la PC). */
export function hoyIso(): string {
  return new Date().toLocaleDateString('en-CA');
}

/** Suma meses a una fecha yyyy-MM-dd como Java (31/01 + 1 mes = 28/02). */
export function sumarMeses(iso: string, meses: number): string {
  const [a, m, d] = iso.split('-').map(Number);
  const total = a * 12 + (m - 1) + meses;
  const anio = Math.floor(total / 12), mes = total % 12;
  const ultimoDia = new Date(Date.UTC(anio, mes + 1, 0)).getUTCDate();
  return `${anio}-${String(mes + 1).padStart(2, '0')}-${String(Math.min(d, ultimoDia)).padStart(2, '0')}`;
}

export function formatoFecha(iso: string): string {
  const [a, m, d] = iso.split('-');
  return `${d}/${m}/${a}`;
}

function textoFrecuencia(meses: number): string {
  return meses === 1 ? 'cada mes' : meses === 12 ? 'cada año' : `cada ${meses} meses`;
}

/** "4 visitas cada 3 meses, desde el 07/02/2027". */
export function resumenRutina(r: RutinaMantenimiento, fechaVenta = hoyIso()): string {
  const primera = r.primeraVisita ?? sumarMeses(fechaVenta, r.frecuenciaMeses);
  return `${r.visitas} ${r.visitas === 1 ? 'visita' : 'visitas'} ${textoFrecuencia(r.frecuenciaMeses)}, desde el ${formatoFecha(primera)}`;
}

/**
 * Botón "Implementar rutinas de mantenimiento" (Nueva venta y conversión de proforma).
 * Sin pulsarlo la venta no programa mantenimientos; al aplicarlo muestra el resumen con "Cambiar" y "Quitar".
 * Uso: `<app-rutina-mantenimiento [(rutina)]="rutina" />`.
 */
@Component({
  selector: 'app-rutina-mantenimiento',
  imports: [FormsModule, IconoComponent],
  template: `
    <div class="rutina" role="group" aria-label="Rutina de mantenimiento">
      @if (editando()) {
        <div class="editor">
          <strong class="titulo"><app-icono nombre="agenda" /> Rutina de mantenimiento</strong>
          <div class="campos">
            <label>
              <span class="form-label">Cada</span>
              <select class="form-select" [ngModel]="frecuencia()" (ngModelChange)="cambiarFrecuencia(+$event)" [disabled]="deshabilitado()">
                @for (f of frecuencias; track f) { <option [value]="f">{{ f === 1 ? '1 mes' : f + ' meses' }}</option> }
              </select>
            </label>
            <label>
              <span class="form-label">Visitas</span>
              <input type="number" class="form-control" min="1" [max]="maxVisitas" [ngModel]="visitas()"
                     (ngModelChange)="visitas.set(+$event)" [disabled]="deshabilitado()">
            </label>
            <label>
              <span class="form-label">Primera visita</span>
              <input type="date" class="form-control" [min]="fechaVenta()" [ngModel]="primera()"
                     (ngModelChange)="elegirPrimera($event)" [disabled]="deshabilitado()">
            </label>
          </div>
          @if (error(); as e) {
            <small class="error-text">{{ e }}</small>
          } @else {
            <div class="fechas" aria-label="Fechas de las visitas">
              @for (f of fechas(); track f) { <span class="badge">{{ f }}</span> }
            </div>
          }
          <div class="acciones">
            <button type="button" class="btn btn-sm btn-outline-secondary" (click)="cancelar()" [disabled]="deshabilitado()">Cancelar</button>
            <button type="button" class="btn btn-sm btn-primary" (click)="aplicar()" [disabled]="!!error() || deshabilitado()">Aplicar</button>
          </div>
        </div>
      } @else if (rutina(); as r) {
        <div class="aplicada">
          <span><app-icono nombre="agenda" /> <strong>Mantenimiento:</strong> {{ resumen(r) }}</span>
          <span class="botones">
            <button type="button" class="btn btn-sm btn-outline-secondary" (click)="abrir()" [disabled]="deshabilitado()">Cambiar</button>
            <button type="button" class="btn btn-sm btn-outline-secondary" (click)="rutina.set(null)" [disabled]="deshabilitado()">Quitar</button>
          </span>
        </div>
      } @else {
        <button type="button" class="btn btn-outline-primary btn-implementar" (click)="abrir()" [disabled]="deshabilitado()">
          <app-icono nombre="agenda" /> Implementar rutinas de mantenimiento
        </button>
        <small class="muted ayuda">La venta incluye equipos de climatización o refrigeración. Si no se implementa una rutina, no se programan visitas.</small>
      }
    </div>
  `,
  styles: `
    .rutina { border: 1px dashed var(--border-color); border-radius: 8px; padding: 12px 14px; background: var(--surface-muted); }
    .btn-implementar { display: inline-flex; align-items: center; gap: 6px; }
    .ayuda { display: block; margin-top: 6px; }
    .titulo { display: inline-flex; align-items: center; gap: 6px; margin-bottom: 8px; }
    .campos { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }
    .campos label { display: flex; flex-direction: column; margin: 0; }
    .fechas { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 10px; }
    .fechas .badge { background: var(--primary-light); color: var(--primary); font-weight: 500; }
    .acciones { display: flex; justify-content: flex-end; gap: 8px; margin-top: 12px; }
    .aplicada { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 8px; }
    .aplicada > span:first-child { display: inline-flex; align-items: center; gap: 6px; flex-wrap: wrap; }
    .botones { display: inline-flex; gap: 6px; }
    .error-text { display: block; margin-top: 8px; color: var(--danger); }
    @media (max-width: 560px) { .campos { grid-template-columns: 1fr; } }
  `,
})
export class RutinaMantenimientoComponent {
  readonly rutina = model<RutinaMantenimiento | null>(null);
  readonly deshabilitado = input(false);
  /** Fecha de la venta (hoy): desde ella se cuentan los meses. */
  readonly fechaVenta = input(hoyIso());

  protected readonly frecuencias = FRECUENCIAS;
  protected readonly maxVisitas = MAX_VISITAS;
  protected readonly editando = signal(false);
  protected readonly frecuencia = signal(3);
  protected readonly visitas = signal(4);
  protected readonly primera = signal('');
  /** Si el vendedor eligió la primera fecha a mano, cambiar la frecuencia ya no la mueve. */
  private primeraManual = false;

  protected readonly error = computed(() => {
    const v = this.visitas();
    if (!Number.isInteger(v) || v < 1 || v > MAX_VISITAS) return `Las visitas deben ser entre 1 y ${MAX_VISITAS}.`;
    if (!this.primera()) return 'Elija la fecha de la primera visita.';
    if (this.primera() < this.fechaVenta()) return 'La primera visita no puede ser anterior a la venta.';
    return null;
  });

  /** Vista previa de todas las fechas. */
  protected readonly fechas = computed(() =>
    Array.from({ length: Math.min(this.visitas() || 0, MAX_VISITAS) }, (_, i) =>
      formatoFecha(sumarMeses(this.primera() || this.fechaVenta(), this.frecuencia() * i))));

  protected resumen(r: RutinaMantenimiento) {
    return resumenRutina(r, this.fechaVenta());
  }

  protected abrir() {
    const r = this.rutina();
    this.frecuencia.set(r?.frecuenciaMeses ?? 3);
    this.visitas.set(r?.visitas ?? 4);
    this.primeraManual = !!r?.primeraVisita;
    this.primera.set(r?.primeraVisita ?? sumarMeses(this.fechaVenta(), this.frecuencia()));
    this.editando.set(true);
  }

  protected cambiarFrecuencia(f: number) {
    this.frecuencia.set(f);
    if (!this.primeraManual) this.primera.set(sumarMeses(this.fechaVenta(), f));
  }

  protected elegirPrimera(fecha: string) {
    this.primera.set(fecha);
    this.primeraManual = true;
  }

  protected aplicar() {
    if (this.error()) return;
    this.rutina.set({ frecuenciaMeses: this.frecuencia(), visitas: this.visitas(), primeraVisita: this.primera() });
    this.editando.set(false);
  }

  protected cancelar() {
    this.editando.set(false);
  }
}
