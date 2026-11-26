import { Component, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ApiService } from '../../../core/services/api-service';
export interface RecordatorioEstado {
  activo: boolean;
  frecuenciaMeses: number;
  anticipacionDias: number;
  fechaSiguiente: string;
  fechaAviso: string;
  correoCliente: string;
  correoOficina: string;
  estadoCliente: string;
  estadoOficina: string;
  errorCliente: string | null;
  errorOficina: string | null;
}
@Component({
  selector: 'app-recordatorio-servicio',
  imports: [FormsModule],
  template: ` <section class="card">
    <div class="card-body">
      <h2>Próximo mantenimiento</h2>
      <p class="muted">
        Recordatorio por correo al cliente y a la oficina. La próxima fecha se calcula desde el
        servicio realizado; coordine la visita antes de asignar un técnico.
      </p>
      @if (error()) {
        <div class="alert alert-danger" role="alert">{{ error() }}</div>
      }
      @if (mensaje()) {
        <div class="alert alert-info" role="status">{{ mensaje() }}</div>
      }
      <form (ngSubmit)="guardar()">
        <div class="campos">
          <label
            ><input type="checkbox" name="activo" [(ngModel)]="activo" /> Activar
            recordatorio</label
          >
          <div>
            <label for="recMeses" class="form-label">Frecuencia en meses</label
            ><input
              id="recMeses"
              name="meses"
              type="number"
              min="1"
              max="120"
              class="form-control"
              [(ngModel)]="meses"
              required
            />
          </div>
          <div>
            <label for="recDias" class="form-label">Avisar días antes</label
            ><input
              id="recDias"
              name="dias"
              type="number"
              min="0"
              max="30"
              class="form-control"
              [(ngModel)]="dias"
              required
            />
          </div>
        </div>
        <button class="btn btn-primary" [disabled]="ocupado()">
          {{ ocupado() ? 'Guardando…' : 'Guardar recordatorio' }}
        </button>
      </form>
      @if (estado(); as r) {
        <p>
          <strong>{{ r.activo ? 'Activo' : 'Desactivado' }}</strong> · Próximo mantenimiento:
          {{ r.fechaSiguiente }} · Aviso: {{ r.fechaAviso }}
        </p>
        <p>
          Cliente: {{ r.correoCliente }} · {{ r.estadoCliente }}
          @if (r.errorCliente) {
            <span class="text-danger">{{ r.errorCliente }}</span>
          }
        </p>
        <p>
          Oficina: {{ r.correoOficina }} · {{ r.estadoOficina }}
          @if (r.errorOficina) {
            <span class="text-danger">{{ r.errorOficina }}</span>
          }
        </p>
        @if (
          r.estadoCliente === 'INCIERTO' ||
          r.estadoOficina === 'INCIERTO' ||
          r.estadoCliente === 'FALLIDO' ||
          r.estadoOficina === 'FALLIDO'
        ) {
          <label
            ><input type="checkbox" [(ngModel)]="confirmar" /> Revisé la entrega y confirmo el
            reintento; un correo incierto podría repetirse.</label
          >
          <button
            type="button"
            class="btn btn-secondary"
            (click)="reintentar()"
            [disabled]="!confirmar || ocupado()"
          >
            Reintentar pendientes
          </button>
        }
      }
    </div>
  </section>`,
})
export class RecordatorioServicioComponent {
  ordenId = input.required<number>();
  private http = inject(HttpClient);
  private api = inject(ApiService);
  estado = signal<RecordatorioEstado | null>(null);
  error = signal('');
  mensaje = signal('');
  ocupado = signal(false);
  activo = false;
  meses = 3;
  dias = 3;
  confirmar = false;
  constructor() {
    effect(() => {
      this.http.get<RecordatorioEstado | null>(this.ruta()).subscribe({
        next: (r) => {
          this.estado.set(r);
          if (r) {
            this.activo = r.activo;
            this.meses = r.frecuenciaMeses || 3;
            this.dias = r.anticipacionDias;
          }
        },
        error: () => this.error.set('No se pudo consultar el recordatorio.'),
      });
    });
  }
  private ruta() {
    return this.api.buildUrl('/v1/admin/ordenes/' + this.ordenId() + '/recordatorio');
  }
  guardar() {
    if (
      !Number.isInteger(this.meses) ||
      this.meses < 1 ||
      this.meses > 120 ||
      !Number.isInteger(this.dias) ||
      this.dias < 0 ||
      this.dias > 30
    ) {
      this.error.set('Use de 1 a 120 meses y de 0 a 30 días.');
      return;
    }
    this.ocupado.set(true);
    this.error.set('');
    this.mensaje.set('');
    this.http
      .put<RecordatorioEstado>(this.ruta(), {
        activo: this.activo,
        frecuenciaMeses: this.meses,
        anticipacionDias: this.dias,
      })
      .subscribe({
        next: (r) => {
          this.estado.set(r);
          this.ocupado.set(false);
          this.mensaje.set('Recordatorio guardado.');
        },
        error: (e) => {
          this.ocupado.set(false);
          this.error.set(e.error?.message ?? 'No se pudo guardar.');
        },
      });
  }
  reintentar() {
    if (!this.confirmar) return;
    this.ocupado.set(true);
    this.http.post<RecordatorioEstado>(this.ruta() + '/reintentar?confirmar=true', {}).subscribe({
      next: (r) => {
        this.estado.set(r);
        this.ocupado.set(false);
        this.confirmar = false;
        this.mensaje.set('Reintento programado para la próxima ejecución.');
      },
      error: (e) => {
        this.ocupado.set(false);
        this.error.set(e.error?.message ?? 'No se pudo programar el reintento.');
      },
    });
  }
}
