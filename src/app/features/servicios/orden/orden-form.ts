import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { OrdenService } from './orden-service';
import { OrdenRequest, TIPOS_ORDEN, TipoOrden } from './orden.model';
import { ClienteService } from '../../clientes/cliente/cliente-service';
import { Cliente } from '../../clientes/cliente/cliente.model';
import { SedeService } from '../../clientes/sede/sede-service';
import { Sede } from '../../clientes/sede/sede.model';
import { EquipoService } from '../../postventa/equipo/equipo-service';
import { Equipo } from '../../postventa/equipo/equipo.model';
import { VentaService } from '../../ventas/venta/venta-service';
import { VentaResponse } from '../../ventas/venta/venta.model';
import { IconoComponent } from '../../../core/layout/icono';

/**
 * Programar o editar una orden de trabajo. Desde Postventa llega con ?mantenimientoId=…&clienteId=…&fecha=…
 * para ejecutar esa visita (tipo MANTENIMIENTO, cliente fijo). La sede, la venta y el equipo se eligen
 * entre los del cliente.
 */
@Component({
  selector: 'app-orden-form',
  imports: [IconoComponent, ReactiveFormsModule, RouterLink],
  templateUrl: './orden-form.html',
})
export class OrdenFormComponent {
  private readonly fb = inject(FormBuilder);
  private readonly ordenService = inject(OrdenService);
  private readonly clienteService = inject(ClienteService);
  private readonly sedeService = inject(SedeService);
  private readonly equipoService = inject(EquipoService);
  private readonly ventaService = inject(VentaService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly tipos = TIPOS_ORDEN;
  readonly id = signal<number | null>(null);
  readonly mantenimientoId = signal<number | null>(null);
  readonly clientes = signal<Cliente[]>([]);
  readonly sedes = signal<Sede[]>([]);
  readonly equipos = signal<Equipo[]>([]);
  readonly ventasCliente = signal<VentaResponse[]>([]);
  readonly tecnicos = signal<string[]>([]);
  readonly error = signal('');
  readonly guardando = signal(false);

  readonly form = this.fb.group({
    tipo: this.fb.nonNullable.control<TipoOrden>('INSTALACION', Validators.required),
    clienteId: this.fb.control<number | null>(null, Validators.required),
    sedeId: this.fb.control<number | null>(null),
    ventaId: this.fb.control<number | null>(null),
    equipoId: this.fb.control<number | null>(null),
    fechaProgramada: this.fb.nonNullable.control(this.hoy(), Validators.required),
    horaInicio: this.fb.nonNullable.control(''),
    duracionMinutos: this.fb.nonNullable.control(120, [Validators.min(15), Validators.max(720)]),
    tecnico: this.fb.nonNullable.control('', [Validators.required, Validators.maxLength(100)]),
    direccion: this.fb.nonNullable.control('', Validators.maxLength(200)),
    descripcion: this.fb.nonNullable.control('', [Validators.required, Validators.maxLength(500)]),
  });

  private readonly tipo = toSignal(this.form.controls.tipo.valueChanges, { initialValue: this.form.controls.tipo.value });
  readonly esInstalacion = computed(() => this.tipo() === 'INSTALACION');

  constructor() {
    this.clienteService.listarTodos().subscribe({ next: (c) => this.clientes.set(c) });
    this.ordenService.tecnicos().subscribe({ next: (t) => this.tecnicos.set(t) });
    // Al elegir el cliente se cargan sus sedes, equipos y ventas
    this.form.controls.clienteId.valueChanges.subscribe((id) => this.cargarDelCliente(id, true));

    const idParam = this.route.snapshot.paramMap.get('id');
    const q = this.route.snapshot.queryParamMap;
    if (idParam) {
      this.id.set(Number(idParam));
      this.ordenService.obtener(Number(idParam)).subscribe({
        next: (o) => {
          this.mantenimientoId.set(o.mantenimientoId);
          this.cargarDelCliente(o.clienteId, false);
          this.form.patchValue({ tipo: o.tipo, clienteId: o.clienteId, sedeId: o.sedeId, ventaId: o.ventaId, equipoId: o.equipoId,
            fechaProgramada: o.fechaProgramada, horaInicio: o.horaInicio ?? '', duracionMinutos: o.duracionMinutos, tecnico: o.tecnico, direccion: o.direccion ?? '', descripcion: o.descripcion }, { emitEvent: false });
          this.form.controls.tipo.setValue(o.tipo);
        },
        error: () => this.error.set('No se pudo cargar la orden.'),
      });
    } else if (q.get('mantenimientoId')) {
      // Ejecutar una visita de Postventa: tipo y cliente quedan fijos
      this.mantenimientoId.set(Number(q.get('mantenimientoId')));
      this.form.patchValue({ tipo: 'MANTENIMIENTO', clienteId: Number(q.get('clienteId')),
        fechaProgramada: q.get('fecha') && q.get('fecha')! >= this.hoy() ? q.get('fecha')! : this.hoy(),
        descripcion: 'Mantenimiento preventivo programado' });
    } else if (q.get('clienteId')) {
      this.form.patchValue({ clienteId: Number(q.get('clienteId')), ventaId: q.get('ventaId') ? Number(q.get('ventaId')) : null });
    }
    // Desde la Agenda: el espacio libre elegido (día, hora y técnico)
    if (!idParam && !q.get('mantenimientoId') && q.get('fecha') && q.get('fecha')! >= this.hoy()) {
      this.form.patchValue({ fechaProgramada: q.get('fecha')! });
    }
    if (!idParam && q.get('hora')) this.form.patchValue({ horaInicio: q.get('hora')! });
    if (!idParam && q.get('tecnico')) this.form.patchValue({ tecnico: q.get('tecnico')! });
  }

  private cargarDelCliente(clienteId: number | null, limpiar: boolean) {
    if (limpiar) this.form.patchValue({ sedeId: null, equipoId: null, ventaId: null }, { emitEvent: false });
    this.sedes.set([]);
    this.equipos.set([]);
    this.ventasCliente.set([]);
    if (!clienteId) return;
    this.sedeService.listar(clienteId).subscribe({ next: (s) => this.sedes.set(s) });
    this.equipoService.listar({ clienteId, estado: 'ACTIVO' }).subscribe({ next: (e) => this.equipos.set(e) });
    this.ventaService.listar({ estado: 'REGISTRADA' }).subscribe({
      next: (v) => this.ventasCliente.set(v.filter((x) => x.clienteId === clienteId).slice(0, 30)),
    });
  }

  guardar() {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.error.set('Complete los campos obligatorios.');
      return;
    }
    const v = this.form.getRawValue();
    const orden: OrdenRequest = {
      tipo: v.tipo,
      clienteId: v.clienteId!,
      sedeId: v.sedeId,
      ventaId: v.ventaId,
      mantenimientoId: this.mantenimientoId(),
      equipoId: v.equipoId,
      fechaProgramada: v.fechaProgramada,
      horaInicio: v.horaInicio || null,
      duracionMinutos: v.horaInicio ? v.duracionMinutos : null,
      tecnico: v.tecnico.trim(),
      direccion: v.direccion.trim() || null,
      descripcion: v.descripcion.trim(),
    };
    const id = this.id();
    this.guardando.set(true);
    this.error.set('');
    (id ? this.ordenService.actualizar(id, orden) : this.ordenService.crear(orden)).subscribe({
      next: (o) => this.router.navigate(['/servicios/ordenes', o.id]),
      error: (err: HttpErrorResponse) => {
        this.guardando.set(false);
        const errores = err.error?.errores as Record<string, string> | undefined;
        this.error.set(errores ? Object.values(errores).join(' · ') : err.error?.message ?? 'No se pudo guardar la orden.');
      },
    });
  }

  invalido(campo: keyof typeof this.form.controls): boolean {
    const c = this.form.controls[campo];
    return c.touched && c.invalid;
  }

  private hoy(): string {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }
}
