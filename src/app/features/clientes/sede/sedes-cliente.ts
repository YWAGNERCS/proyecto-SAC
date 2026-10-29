import { Component, effect, inject, input, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../../core/auth/auth.service';
import { SedeService } from './sede-service';
import { Sede } from './sede.model';

/**
 * Sedes (locales) de un cliente, dentro de su ficha. Se agregan y editan en la misma tarjeta;
 * eliminar es solo del administrador (el backend lo vuelve a exigir con 403).
 */
@Component({
  selector: 'app-sedes-cliente',
  imports: [ReactiveFormsModule],
  templateUrl: './sedes-cliente.html',
})
export class SedesClienteComponent {
  private readonly fb = inject(FormBuilder);
  private readonly sedeService = inject(SedeService);
  readonly esAdmin = inject(AuthService).esAdmin;

  readonly clienteId = input.required<number>();

  readonly sedes = signal<Sede[]>([]);
  readonly error = signal('');
  readonly guardando = signal(false);
  /** null = formulario cerrado · 0 = sede nueva · id = editando esa sede */
  readonly editando = signal<number | null>(null);

  readonly form = this.fb.nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(100)]],
    direccion: ['', [Validators.maxLength(200)]],
    contacto: ['', [Validators.maxLength(100)]],
    telefono: ['', [Validators.maxLength(20)]],
  });

  constructor() {
    effect(() => this.cargar(this.clienteId()));
  }

  private cargar(clienteId: number) {
    this.sedeService.listar(clienteId).subscribe({
      next: (s) => this.sedes.set(s),
      error: () => this.error.set('No se pudieron cargar las sedes.'),
    });
  }

  nueva() {
    this.form.reset();
    this.error.set('');
    this.editando.set(0);
  }

  editar(s: Sede) {
    this.form.setValue({ nombre: s.nombre, direccion: s.direccion ?? '', contacto: s.contacto ?? '', telefono: s.telefono ?? '' });
    this.error.set('');
    this.editando.set(s.id);
  }

  cancelar() {
    this.editando.set(null);
  }

  guardar() {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const datos = { nombre: v.nombre.trim(), direccion: v.direccion.trim() || null, contacto: v.contacto.trim() || null, telefono: v.telefono.trim() || null };
    const id = this.editando();
    const peticion = id ? this.sedeService.actualizar(this.clienteId(), id, datos) : this.sedeService.crear(this.clienteId(), datos);
    this.guardando.set(true);
    peticion.subscribe({
      next: () => {
        this.guardando.set(false);
        this.editando.set(null);
        this.cargar(this.clienteId());
      },
      error: (err: HttpErrorResponse) => {
        this.guardando.set(false);
        this.error.set(err.error?.message ?? 'No se pudo guardar la sede.');
      },
    });
  }

  eliminar(s: Sede) {
    if (!confirm(`¿Eliminar la sede "${s.nombre}"?`)) return;
    this.sedeService.eliminar(this.clienteId(), s.id).subscribe({
      next: () => this.cargar(this.clienteId()),
      error: (err: HttpErrorResponse) => this.error.set(err.error?.message ?? 'No se pudo eliminar la sede.'),
    });
  }
}
