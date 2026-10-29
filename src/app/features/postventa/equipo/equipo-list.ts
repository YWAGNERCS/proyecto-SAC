import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { EquipoService } from './equipo-service';
import { Equipo, FRECUENCIAS, etiquetaFrecuencia } from './equipo.model';
import { AuthService } from '../../../core/auth/auth.service';
import { ClienteService } from '../../clientes/cliente/cliente-service';
import { Cliente } from '../../clientes/cliente/cliente.model';
import { SedeService } from '../../clientes/sede/sede-service';
import { Sede } from '../../clientes/sede/sede.model';
import { ProductoService } from '../../catalogo/producto/producto-service';
import { Producto, esEquipo } from '../../catalogo/producto/producto.model';

/**
 * Parque instalado: equipos de JD en los locales de los clientes, con serie, garantía y plan de
 * mantenimiento. Se registran al cerrar una orden de instalación o a mano (equipos anteriores al sistema).
 */
@Component({
  selector: 'app-equipo-list',
  imports: [FormsModule, ReactiveFormsModule, RouterLink],
  templateUrl: './equipo-list.html',
})
export class EquipoListComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly equipoService = inject(EquipoService);
  private readonly clienteService = inject(ClienteService);
  private readonly sedeService = inject(SedeService);
  private readonly productoService = inject(ProductoService);
  readonly esAdmin = inject(AuthService).esAdmin;

  readonly equipos = signal<Equipo[]>([]);
  readonly clientes = signal<Cliente[]>([]);
  readonly productos = signal<Producto[]>([]);
  readonly sedesDialogo = signal<Sede[]>([]);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly mensaje = signal('');
  readonly errorDialogo = signal('');
  readonly guardando = signal(false);
  /** null = cerrado · 0 = registrar · id = editar ese equipo */
  readonly editando = signal<number | null>(null);
  readonly retirando = signal<Equipo | null>(null);
  readonly frecuencias = FRECUENCIAS;
  readonly etiquetaFrecuencia = etiquetaFrecuencia;
  readonly hoy = this.isoHoy();

  readonly equiposCatalogo = computed(() => this.productos().filter((p) => esEquipo(p.sector)));

  clienteId: number | null = null;
  estado = 'ACTIVO';
  garantia: '' | 'SI' | 'NO' = '';

  readonly form = this.fb.group({
    clienteId: this.fb.control<number | null>(null, Validators.required),
    sedeId: this.fb.control<number | null>(null),
    productoId: this.fb.control<number | null>(null, Validators.required),
    numeroSerie: this.fb.nonNullable.control('', Validators.maxLength(60)),
    ubicacion: this.fb.nonNullable.control('', Validators.maxLength(100)),
    fechaInstalacion: this.fb.nonNullable.control(this.hoy, Validators.required),
    mesesGarantia: this.fb.nonNullable.control(12, [Validators.required, Validators.min(0), Validators.max(120)]),
    frecuenciaMeses: this.fb.control<number | null>(null),
    notas: this.fb.nonNullable.control('', Validators.maxLength(300)),
  });
  readonly motivo = this.fb.nonNullable.control('', [Validators.required, Validators.minLength(5), Validators.maxLength(200)]);

  ngOnInit() {
    this.clienteService.listarTodos().subscribe({ next: (c) => this.clientes.set(c) });
    this.productoService.listarTodos().subscribe({ next: (p) => this.productos.set(p) });
    this.form.controls.clienteId.valueChanges.subscribe((id) => {
      this.form.controls.sedeId.setValue(null);
      this.cargarSedes(id);
    });
    this.cargar();
  }

  cargar() {
    this.loading.set(true);
    this.error.set('');
    this.equipoService.listar({ clienteId: this.clienteId, estado: this.estado,
      enGarantia: this.garantia === '' ? null : this.garantia === 'SI' }).subscribe({
      next: (e) => { this.equipos.set(e); this.loading.set(false); },
      error: () => { this.loading.set(false); this.error.set('No se pudieron cargar los equipos.'); },
    });
  }

  private cargarSedes(clienteId: number | null) {
    this.sedesDialogo.set([]);
    if (clienteId) this.sedeService.listar(clienteId).subscribe({ next: (s) => this.sedesDialogo.set(s) });
  }

  registrar() {
    this.form.reset({ fechaInstalacion: this.hoy, mesesGarantia: 12, clienteId: this.clienteId });
    this.form.controls.clienteId.enable();
    this.form.controls.productoId.enable();
    this.form.controls.fechaInstalacion.enable();
    this.errorDialogo.set('');
    this.editando.set(0);
  }

  editar(e: Equipo) {
    this.cargarSedes(e.clienteId);
    this.form.reset({ clienteId: e.clienteId, sedeId: e.sedeId, productoId: e.productoId, numeroSerie: e.numeroSerie ?? '',
      ubicacion: e.ubicacion ?? '', fechaInstalacion: e.fechaInstalacion, mesesGarantia: e.mesesGarantia,
      frecuenciaMeses: e.frecuenciaMeses, notas: e.notas ?? '' }, { emitEvent: false });
    // El cliente, el modelo y la fecha de instalación no se cambian: para eso se retira y se registra otro
    this.form.controls.clienteId.disable({ emitEvent: false });
    this.form.controls.productoId.disable();
    this.form.controls.fechaInstalacion.disable();
    this.errorDialogo.set('');
    this.editando.set(e.id);
  }

  guardar() {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.errorDialogo.set('Elija el cliente y el equipo, y revise la garantía.');
      return;
    }
    const v = this.form.getRawValue();
    const id = this.editando();
    const peticion = id
      ? this.equipoService.actualizar(id, { sedeId: v.sedeId, numeroSerie: v.numeroSerie.trim() || null, ubicacion: v.ubicacion.trim() || null,
          mesesGarantia: Number(v.mesesGarantia), frecuenciaMeses: v.frecuenciaMeses, notas: v.notas.trim() || null })
      : this.equipoService.registrar({ clienteId: v.clienteId!, sedeId: v.sedeId, productoId: v.productoId!,
          numeroSerie: v.numeroSerie.trim() || null, ubicacion: v.ubicacion.trim() || null, fechaInstalacion: v.fechaInstalacion,
          mesesGarantia: Number(v.mesesGarantia), frecuenciaMeses: v.frecuenciaMeses, ventaId: null, notas: v.notas.trim() || null });
    this.guardando.set(true);
    this.errorDialogo.set('');
    peticion.subscribe({
      next: (e) => {
        this.guardando.set(false);
        this.editando.set(null);
        this.mensaje.set(`${e.productoNombre} ${id ? 'actualizado' : 'registrado'}`
          + (e.proximoMantenimiento ? `: próximo mantenimiento el ${e.proximoMantenimiento}.` : '.'));
        this.cargar();
      },
      error: (err: HttpErrorResponse) => {
        this.guardando.set(false);
        const errores = err.error?.errores as Record<string, string> | undefined;
        this.errorDialogo.set(errores ? Object.values(errores).join(' · ') : err.error?.message ?? 'No se pudo guardar el equipo.');
      },
    });
  }

  abrirRetiro(e: Equipo) {
    this.motivo.reset();
    this.errorDialogo.set('');
    this.retirando.set(e);
  }

  confirmarRetiro() {
    const e = this.retirando();
    if (!e) return;
    if (this.motivo.invalid) {
      this.motivo.markAsTouched();
      this.errorDialogo.set('Indique el motivo (al menos 5 caracteres).');
      return;
    }
    this.guardando.set(true);
    this.equipoService.retirar(e.id, this.motivo.value.trim()).subscribe({
      next: () => {
        this.guardando.set(false);
        this.retirando.set(null);
        this.mensaje.set(`${e.productoNombre} retirado: sus visitas pendientes se cancelaron.`);
        this.cargar();
      },
      error: (err: HttpErrorResponse) => {
        this.guardando.set(false);
        this.errorDialogo.set(err.error?.message ?? 'No se pudo retirar el equipo.');
      },
    });
  }

  cerrarDialogo() {
    if (!this.guardando()) {
      this.editando.set(null);
      this.retirando.set(null);
    }
  }

  private isoHoy(): string {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }
}
