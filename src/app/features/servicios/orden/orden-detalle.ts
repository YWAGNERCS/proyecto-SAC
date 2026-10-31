import { Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormArray, FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { OrdenService } from './orden-service';
import { OrdenTrabajo, etiquetaTipo } from './orden.model';
import { AuthService } from '../../../core/auth/auth.service';
import { ProductoService } from '../../catalogo/producto/producto-service';
import { Producto, esEquipo } from '../../catalogo/producto/producto.model';
import { FRECUENCIAS, etiquetaFrecuencia } from '../../postventa/equipo/equipo.model';

type MaterialForm = FormGroup<{ productoId: FormControl<number | null>; cantidad: FormControl<number> }>;
type EquipoForm = FormGroup<{
  productoId: FormControl<number | null>;
  numeroSerie: FormControl<string>;
  ubicacion: FormControl<string>;
  mesesGarantia: FormControl<number>;
  frecuenciaMeses: FormControl<number | null>;
}>;
type ServicioForm = FormGroup<{ productoId: FormControl<number | null>; cantidad: FormControl<number> }>;

/**
 * Detalle de una orden de trabajo. Mientras está PROGRAMADA se edita, se cierra (cabecera-detalle:
 * materiales usados y, si es instalación, equipos instalados) o la cancela el administrador.
 * Ya REALIZADA, se factura la mano de obra.
 */
@Component({
  selector: 'app-orden-detalle',
  imports: [ReactiveFormsModule, RouterLink, DecimalPipe],
  templateUrl: './orden-detalle.html',
})
export class OrdenDetalleComponent {
  private readonly fb = inject(FormBuilder);
  private readonly ordenService = inject(OrdenService);
  private readonly productoService = inject(ProductoService);
  private readonly auth = inject(AuthService);
  /** ADMIN o asistente con el permiso SERVICIOS delegado (el backend exige lo mismo). */
  readonly puedeServicios = computed(() => this.auth.puede('SERVICIOS'));

  readonly orden = signal<OrdenTrabajo | null>(null);
  readonly productos = signal<Producto[]>([]);
  readonly error = signal('');
  readonly mensaje = signal('');
  readonly errorDialogo = signal('');
  readonly guardando = signal(false);
  readonly dialogo = signal<'CERRAR' | 'FACTURAR' | 'CANCELAR' | null>(null);
  readonly etiquetaTipo = etiquetaTipo;
  readonly etiquetaFrecuencia = etiquetaFrecuencia;
  readonly frecuencias = FRECUENCIAS;
  readonly hoy = this.isoHoy();

  readonly materialesDisponibles = computed(() => this.productos().filter((p) => p.sector === 'MATERIAL_SERVICIO'));
  readonly equiposCatalogo = computed(() => this.productos().filter((p) => esEquipo(p.sector)));
  readonly servicios = computed(() => this.productos().filter((p) => p.sector === 'SERVICIO'));

  readonly cierreForm = this.fb.nonNullable.group({
    fechaRealizada: [this.hoy, Validators.required],
    trabajoRealizado: ['', [Validators.required, Validators.maxLength(1000)]],
    recibidoPor: ['', [Validators.required, Validators.maxLength(100)]],
    observaciones: ['', Validators.maxLength(500)],
    materiales: this.fb.array<MaterialForm>([]),
    equipos: this.fb.array<EquipoForm>([]),
  });

  readonly facturaForm = this.fb.nonNullable.group({
    tipoPago: ['CREDITO' as 'CONTADO' | 'CREDITO'],
    diasCredito: [30, [Validators.min(1), Validators.max(365)]],
    items: this.fb.array<ServicioForm>([]),
  });

  readonly motivo = this.fb.nonNullable.control('', [Validators.required, Validators.minLength(5), Validators.maxLength(300)]);

  get materiales(): FormArray<MaterialForm> {
    return this.cierreForm.controls.materiales;
  }

  get equipos(): FormArray<EquipoForm> {
    return this.cierreForm.controls.equipos;
  }

  get items(): FormArray<ServicioForm> {
    return this.facturaForm.controls.items;
  }

  constructor() {
    const id = Number(inject(ActivatedRoute).snapshot.paramMap.get('id'));
    this.cargar(id);
    this.productoService.listarTodos().subscribe({ next: (p) => this.productos.set(p) });
  }

  private cargar(id: number) {
    this.ordenService.obtener(id).subscribe({
      next: (o) => this.orden.set(o),
      error: (err: HttpErrorResponse) => this.error.set(err.status === 404 ? 'La orden no existe.' : 'No se pudo cargar la orden.'),
    });
  }

  // ── Cerrar ──
  abrirCerrar() {
    this.cierreForm.reset({ fechaRealizada: this.hoy });
    this.materiales.clear();
    this.equipos.clear();
    if (this.orden()?.tipo === 'INSTALACION') this.agregarEquipo();
    this.abrir('CERRAR');
  }

  agregarMaterial() {
    this.materiales.push(this.fb.group({
      productoId: this.fb.control<number | null>(null, Validators.required),
      cantidad: this.fb.nonNullable.control(1, [Validators.required, Validators.min(1)]),
    }));
  }

  agregarEquipo() {
    this.equipos.push(this.fb.group({
      productoId: this.fb.control<number | null>(null, Validators.required),
      numeroSerie: this.fb.nonNullable.control('', Validators.maxLength(60)),
      ubicacion: this.fb.nonNullable.control('', Validators.maxLength(100)),
      mesesGarantia: this.fb.nonNullable.control(12, [Validators.required, Validators.min(0), Validators.max(120)]),
      frecuenciaMeses: this.fb.control<number | null>(3),
    }));
  }

  stockDe(productoId: number | null): number | null {
    return this.productos().find((p) => p.id === productoId)?.stockActual ?? null;
  }

  confirmarCierre() {
    const o = this.orden();
    if (!o) return;
    if (this.cierreForm.invalid) {
      this.cierreForm.markAllAsTouched();
      this.errorDialogo.set('Complete lo que se hizo, quién lo recibió, y el producto y la cantidad de cada línea.');
      return;
    }
    const v = this.cierreForm.getRawValue();
    this.enviar(this.ordenService.cerrar(o.id, {
      fechaRealizada: v.fechaRealizada || null,
      trabajoRealizado: v.trabajoRealizado.trim(),
      recibidoPor: v.recibidoPor.trim(),
      observaciones: v.observaciones.trim() || null,
      materiales: v.materiales.map((m) => ({ productoId: m.productoId!, cantidad: Number(m.cantidad) })),
      equipos: v.equipos.map((e) => ({ productoId: e.productoId!, numeroSerie: e.numeroSerie.trim() || null,
        ubicacion: e.ubicacion.trim() || null, mesesGarantia: Number(e.mesesGarantia), frecuenciaMeses: e.frecuenciaMeses })),
    }), (r) => `Orden #${r.id} cerrada: ${r.materiales.length} material(es) descontado(s) del stock`
      + (r.equiposInstalados.length ? ` y ${r.equiposInstalados.length} equipo(s) registrado(s).` : '.'));
  }

  // ── Facturar ──
  abrirFacturar() {
    this.facturaForm.reset({ tipoPago: 'CREDITO', diasCredito: 30 });
    this.totalFactura.set(0);
    this.mezclaMonedas.set(false);
    this.items.clear();
    this.items.push(this.fb.group({
      productoId: this.fb.control<number | null>(null, Validators.required),
      cantidad: this.fb.nonNullable.control(1, [Validators.required, Validators.min(1)]),
    }));
    this.abrir('FACTURAR');
  }

  agregarServicio() {
    this.items.push(this.fb.group({
      productoId: this.fb.control<number | null>(null, Validators.required),
      cantidad: this.fb.nonNullable.control(1, [Validators.required, Validators.min(1)]),
    }));
  }

  /**
   * Vista previa: precio sin IGV × cantidad, + 18 %, en la moneda del primer servicio (la de la venta).
   * Si se mezclan monedas solo es aproximada: el total definitivo, convertido, lo calcula el servidor.
   */
  readonly totalFactura = signal(0);
  readonly monedaFactura = signal<string>('SOLES');
  readonly mezclaMonedas = signal(false);

  recalcular() {
    const elegidos = this.items.getRawValue()
      .map((i) => ({ p: this.servicios().find((x) => x.id === i.productoId), cantidad: Number(i.cantidad) }))
      .filter((x) => x.p);
    const moneda = elegidos[0]?.p?.moneda ?? 'SOLES';
    const sub = elegidos.filter((x) => x.p!.moneda === moneda)
      .reduce((s, x) => s + Number(x.p!.precioVenta ?? 0) * x.cantidad, 0);
    this.monedaFactura.set(moneda);
    this.mezclaMonedas.set(elegidos.some((x) => x.p!.moneda !== moneda));
    this.totalFactura.set(Math.round(sub * 1.18 * 100) / 100);
  }

  confirmarFactura() {
    const o = this.orden();
    if (!o) return;
    if (this.facturaForm.invalid) {
      this.facturaForm.markAllAsTouched();
      this.errorDialogo.set('Elija el servicio y la cantidad de cada línea.');
      return;
    }
    const v = this.facturaForm.getRawValue();
    this.enviar(this.ordenService.facturar(o.id, {
      items: v.items.map((i) => ({ productoId: i.productoId!, cantidad: Number(i.cantidad), descuentoPorcentaje: null })),
      tipoPago: v.tipoPago,
      diasCredito: v.tipoPago === 'CREDITO' ? Number(v.diasCredito) : null,
    }), (r) => `Orden #${r.id} facturada con la venta #${r.ventaFacturadaId}. Si el servicio supera S/ 700, la venta ya incluye la detracción.`);
  }

  // ── Cancelar ──
  abrirCancelar() {
    this.motivo.reset();
    this.abrir('CANCELAR');
  }

  confirmarCancelacion() {
    const o = this.orden();
    if (!o) return;
    if (this.motivo.invalid) {
      this.motivo.markAsTouched();
      this.errorDialogo.set('Indique el motivo (al menos 5 caracteres).');
      return;
    }
    this.enviar(this.ordenService.cancelar(o.id, this.motivo.value.trim()), (r) => `Orden #${r.id} cancelada.`);
  }

  cerrarDialogo() {
    if (!this.guardando()) this.dialogo.set(null);
  }

  private abrir(d: 'CERRAR' | 'FACTURAR' | 'CANCELAR') {
    this.errorDialogo.set('');
    this.mensaje.set('');
    this.dialogo.set(d);
  }

  private enviar(peticion: ReturnType<OrdenService['cerrar']>, exito: (o: OrdenTrabajo) => string) {
    this.guardando.set(true);
    this.errorDialogo.set('');
    peticion.subscribe({
      next: (o) => {
        this.guardando.set(false);
        this.dialogo.set(null);
        this.orden.set(o);
        this.mensaje.set(exito(o));
        this.productoService.listarTodos().subscribe({ next: (p) => this.productos.set(p) });
      },
      error: (err: HttpErrorResponse) => {
        this.guardando.set(false);
        const errores = err.error?.errores as Record<string, string> | undefined;
        this.errorDialogo.set(errores ? Object.values(errores).join(' · ') : err.error?.message ?? 'No se pudo completar la operación.');
      },
    });
  }

  private isoHoy(): string {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }
}
