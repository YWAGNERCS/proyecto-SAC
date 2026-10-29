import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ContratoService } from './contrato-service';
import { Contrato } from './contrato.model';
import { AuthService } from '../../../core/auth/auth.service';
import { ClienteService } from '../../clientes/cliente/cliente-service';
import { Cliente } from '../../clientes/cliente/cliente.model';
import { SedeService } from '../../clientes/sede/sede-service';
import { Sede } from '../../clientes/sede/sede.model';
import { FRECUENCIAS, etiquetaFrecuencia } from '../equipo/equipo.model';

/**
 * Contratos de mantenimiento: entre dos fechas, una visita cada N meses a un precio por visita.
 * Al crearlo se programan sus visitas en Postventa. Crear y cancelar es solo del administrador.
 */
@Component({
  selector: 'app-contrato-list',
  imports: [FormsModule, ReactiveFormsModule, RouterLink, DecimalPipe],
  templateUrl: './contrato-list.html',
})
export class ContratoListComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly contratoService = inject(ContratoService);
  private readonly clienteService = inject(ClienteService);
  private readonly sedeService = inject(SedeService);
  readonly esAdmin = inject(AuthService).esAdmin;

  readonly contratos = signal<Contrato[]>([]);
  readonly clientes = signal<Cliente[]>([]);
  readonly sedesDialogo = signal<Sede[]>([]);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly mensaje = signal('');
  readonly errorDialogo = signal('');
  readonly guardando = signal(false);
  readonly creando = signal(false);
  readonly cancelando = signal<Contrato | null>(null);
  /** Las frecuencias del contrato (sin la opción "sin plan" de los equipos). */
  readonly frecuencias = FRECUENCIAS.filter((f) => f.valor !== null);
  readonly etiquetaFrecuencia = etiquetaFrecuencia;

  clienteId: number | null = null;
  estado = 'VIGENTE';

  private readonly hoy = this.iso(new Date());
  readonly form = this.fb.group({
    clienteId: this.fb.control<number | null>(null, Validators.required),
    sedeId: this.fb.control<number | null>(null),
    fechaInicio: this.fb.nonNullable.control(this.hoy, Validators.required),
    fechaFin: this.fb.nonNullable.control(this.iso(new Date(new Date().getFullYear() + 1, new Date().getMonth(), new Date().getDate() - 1)),
      Validators.required),
    frecuenciaMeses: this.fb.nonNullable.control(1, Validators.required),
    montoVisita: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.01)]),
    moneda: this.fb.nonNullable.control<'SOLES' | 'DOLARES'>('SOLES'),
    descripcion: this.fb.nonNullable.control('', [Validators.required, Validators.maxLength(300)]),
  });
  readonly motivo = this.fb.nonNullable.control('', [Validators.required, Validators.minLength(5), Validators.maxLength(300)]);

  /** Vista previa de cuántas visitas tendrá (desde hoy) y cuánto suma el contrato. */
  private readonly valor = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });
  readonly vistaPrevia = computed(() => {
    const v = this.valor();
    if (!v.fechaInicio || !v.fechaFin || !v.frecuenciaMeses) return null;
    let visitas = 0;
    const fin = new Date(v.fechaFin + 'T00:00');
    for (let f = new Date(v.fechaInicio + 'T00:00'); f <= fin; f = new Date(f.getFullYear(), f.getMonth() + Number(v.frecuenciaMeses), f.getDate())) {
      if (this.iso(f) >= this.hoy) visitas++;
    }
    return { visitas, total: visitas * (Number(v.montoVisita) || 0) };
  });

  ngOnInit() {
    this.clienteService.listarTodos().subscribe({ next: (c) => this.clientes.set(c) });
    this.form.controls.clienteId.valueChanges.subscribe((id) => {
      this.form.controls.sedeId.setValue(null);
      this.sedesDialogo.set([]);
      if (id) this.sedeService.listar(id).subscribe({ next: (s) => this.sedesDialogo.set(s) });
    });
    this.cargar();
  }

  cargar() {
    this.loading.set(true);
    this.contratoService.listar({ clienteId: this.clienteId, estado: this.estado }).subscribe({
      next: (c) => { this.contratos.set(c); this.loading.set(false); },
      error: () => { this.loading.set(false); this.error.set('No se pudieron cargar los contratos.'); },
    });
  }

  nuevo() {
    this.form.reset();
    this.form.controls.clienteId.setValue(this.clienteId);
    this.errorDialogo.set('');
    this.creando.set(true);
  }

  guardar() {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.errorDialogo.set('Complete el cliente, las fechas, el monto por visita y qué cubre el contrato.');
      return;
    }
    const v = this.form.getRawValue();
    this.guardando.set(true);
    this.errorDialogo.set('');
    this.contratoService.crear({ clienteId: v.clienteId!, sedeId: v.sedeId, fechaInicio: v.fechaInicio, fechaFin: v.fechaFin,
      frecuenciaMeses: Number(v.frecuenciaMeses), montoVisita: Number(v.montoVisita), moneda: v.moneda, descripcion: v.descripcion.trim() })
      .subscribe({
        next: (k) => {
          this.guardando.set(false);
          this.creando.set(false);
          this.mensaje.set(`Contrato #${k.id} creado: ${k.visitasPendientes} visita(s) programada(s) en Postventa.`);
          this.cargar();
        },
        error: (err: HttpErrorResponse) => {
          this.guardando.set(false);
          const errores = err.error?.errores as Record<string, string> | undefined;
          this.errorDialogo.set(errores ? Object.values(errores).join(' · ') : err.error?.message ?? 'No se pudo crear el contrato.');
        },
      });
  }

  abrirCancelacion(k: Contrato) {
    this.motivo.reset();
    this.errorDialogo.set('');
    this.cancelando.set(k);
  }

  confirmarCancelacion() {
    const k = this.cancelando();
    if (!k) return;
    if (this.motivo.invalid) {
      this.motivo.markAsTouched();
      this.errorDialogo.set('Indique el motivo (al menos 5 caracteres).');
      return;
    }
    this.guardando.set(true);
    this.contratoService.cancelar(k.id, this.motivo.value.trim()).subscribe({
      next: () => {
        this.guardando.set(false);
        this.cancelando.set(null);
        this.mensaje.set(`Contrato #${k.id} cancelado: sus visitas pendientes se cancelaron.`);
        this.cargar();
      },
      error: (err: HttpErrorResponse) => {
        this.guardando.set(false);
        this.errorDialogo.set(err.error?.message ?? 'No se pudo cancelar el contrato.');
      },
    });
  }

  cerrarDialogo() {
    if (!this.guardando()) {
      this.creando.set(false);
      this.cancelando.set(null);
    }
  }

  simbolo(moneda: string): string {
    return moneda === 'DOLARES' ? 'US$' : 'S/';
  }

  private iso(d: Date): string {
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }
}
