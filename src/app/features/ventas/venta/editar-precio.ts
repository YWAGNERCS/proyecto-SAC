import { Component, OnInit, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MOTIVO_PRECIO_MAXIMO, errorPrecioEditado } from './precio-editado';

/** Lo que devuelve el diálogo al guardar. */
export interface PrecioElegido {
  precio: number;
  motivo: string;
}

/**
 * Diálogo "Editar precio" de una línea de venta o de proforma (auditoría M1): el nuevo precio (solo puede subir) y
 * el motivo. Solo cambia esa línea; el catálogo no. Lo usan el formulario de venta directa y el de proforma.
 */
@Component({
  selector: 'app-editar-precio',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="dialogo-fondo" (click)="cancelado.emit()">
      <form class="card dialogo" role="dialog" aria-modal="true" aria-labelledby="tituloEditarPrecio"
            (ngSubmit)="guardar()" (click)="$event.stopPropagation()" novalidate>
        <h3 id="tituloEditarPrecio" style="margin: 0 0 4px 0; font-weight: 600;">Editar precio</h3>
        <p class="muted" style="margin: 0 0 12px 0;">
          {{ producto() }} · precio de catálogo <strong>{{ simbolo() }} {{ catalogo().toFixed(2) }}</strong> (sin IGV).
          Solo cambia esta línea: el catálogo no se modifica. Queda en el historial quién lo cambió y por qué.
        </p>
        @if (aviso()) {
          <div class="alert alert-warning" role="alert" style="margin: 0 0 12px 0;">{{ aviso() }}</div>
        }
        <label class="form-label" for="nuevoPrecio">Nuevo precio unitario sin IGV ({{ simbolo() }})</label>
        <input id="nuevoPrecio" name="precio" type="number" step="0.01" [min]="catalogo()" class="form-control"
               [(ngModel)]="precio" style="margin-bottom: 12px;">
        <label class="form-label" for="motivoPrecio">Motivo</label>
        <input id="motivoPrecio" name="motivo" type="text" class="form-control" [maxlength]="maximo"
               [(ngModel)]="motivo" placeholder="Ej.: subió el costo del proveedor">
        <small class="muted">Para bajar el precio use el descuento de la línea.</small>
        @if (error()) {
          <div class="alert alert-danger" role="alert" style="margin-top: 12px;">{{ error() }}</div>
        }
        <div class="d-flex justify-content-end gap-2" style="margin-top: 16px;">
          @if (precioActual() !== null) {
            <button type="button" class="btn btn-outline-secondary" (click)="quitado.emit()"
                    title="Volver al precio de catálogo">Usar el de catálogo</button>
          }
          <button type="button" class="btn btn-secondary" (click)="cancelado.emit()">Cancelar</button>
          <button type="submit" class="btn btn-primary">Guardar precio</button>
        </div>
      </form>
    </div>
  `,
})
export class EditarPrecioComponent implements OnInit {
  readonly producto = input.required<string>();
  readonly simbolo = input.required<string>();
  /** Precio de catálogo en la moneda del documento. */
  readonly catalogo = input.required<number>();
  /** Precio ya editado de la línea (null = el de catálogo). */
  readonly precioActual = input<number | null>(null);
  readonly motivoActual = input<string>('');
  /** Aviso de la última compra (si la hay). */
  readonly aviso = input<string | null>(null);

  readonly guardado = output<PrecioElegido>();
  readonly quitado = output<void>();
  readonly cancelado = output<void>();

  readonly maximo = MOTIVO_PRECIO_MAXIMO;
  precio: number | null = null;
  motivo = '';
  readonly error = signal('');

  ngOnInit() {
    this.precio = this.precioActual() ?? Math.round(this.catalogo() * 100) / 100;
    this.motivo = this.motivoActual();
  }

  guardar() {
    const error = errorPrecioEditado(this.catalogo(), this.precio, this.motivo);
    if (error) {
      this.error.set(error);
      return;
    }
    this.guardado.emit({ precio: Math.round(Number(this.precio) * 100) / 100, motivo: this.motivo.trim() });
  }
}
