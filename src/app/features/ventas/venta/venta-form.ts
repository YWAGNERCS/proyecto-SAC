import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormArray, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { VentaService } from './venta-service';
import { ClienteService } from '../../clientes/cliente/cliente-service';
import { ProductoService } from '../../catalogo/producto/producto-service';
import { VentaDirectaRequest } from './venta.model';
import { Cliente } from '../../clientes/cliente/cliente.model';
import { Producto, precioVenta } from '../../catalogo/producto/producto.model';

const IGV = 0.18;
const MAX_SUGERENCIAS = 8;

/** Validador de la colección completa: el mismo producto no puede repetirse en dos líneas. */
function sinProductosRepetidos(control: AbstractControl): ValidationErrors | null {
  const ids = (control as FormArray).controls
    .map((linea) => Number(linea.get('productoId')?.value))
    .filter((id) => id > 0);
  return new Set(ids).size !== ids.length ? { productoRepetido: true } : null;
}

@Component({
  selector: 'app-venta-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './venta-form.html',
  styleUrl: './venta-form.css',
})
export class VentaFormComponent implements OnInit {
  private fb = inject(FormBuilder);
  private ventaService = inject(VentaService);
  private clienteService = inject(ClienteService);
  private productoService = inject(ProductoService);
  private router = inject(Router);

  clientes = signal<Cliente[]>([]);
  productos = signal<Producto[]>([]);
  errorMessage = signal('');
  loading = signal(false);
  /** Índice de la línea cuyo autocomplete está abierto (-1 = ninguno). */
  lineaAbierta = signal(-1);

  readonly form = this.fb.nonNullable.group({
    clienteId: [0, [Validators.min(1)]],
    tipoPago: ['CONTADO' as 'CONTADO' | 'CREDITO'],
    diasCredito: [30, [Validators.min(1)]],
    detalles: this.fb.array([this.crearLinea()], [Validators.minLength(1), sinProductosRepetidos]),
  });

  get detalles() {
    return this.form.controls.detalles;
  }

  /** Valor de las líneas como señal, para que los computed se recalculen solos. */
  private readonly lineas = toSignal(this.detalles.valueChanges, {
    initialValue: this.detalles.getRawValue(),
  });
  private readonly tipoPago = toSignal(this.form.controls.tipoPago.valueChanges, {
    initialValue: this.form.controls.tipoPago.value,
  });

  /** Producto elegido en cada línea (undefined si aún no se eligió). */
  readonly productosPorLinea = computed(() =>
    this.lineas().map((l) => this.productos().find((p) => p.id === Number(l.productoId))),
  );

  /** Vista previa: el subtotal real lo calcula el backend con el precio vigente al guardar. */
  readonly subtotales = computed(() =>
    this.lineas().map((l, i) => {
      const producto = this.productosPorLinea()[i];
      return producto ? precioVenta(producto) * Number(l.cantidad || 0) : 0;
    }),
  );
  readonly subtotal = computed(() => this.subtotales().reduce((suma, s) => suma + s, 0));
  readonly igv = computed(() => this.subtotal() * IGV);
  readonly total = computed(() => this.subtotal() + this.igv());

  /** Moneda de la venta: la de los productos elegidos (no se permite mezclar). */
  readonly monedas = computed(
    () => new Set(this.productosPorLinea().filter((p) => !!p).map((p) => p!.moneda ?? 'SOLES')),
  );
  readonly monedaMixta = computed(() => this.monedas().size > 1);
  readonly simbolo = computed(() => ([...this.monedas()][0] === 'DOLARES' ? 'US$' : 'S/'));

  readonly esCredito = computed(() => this.tipoPago() === 'CREDITO');

  ngOnInit() {
    this.clienteService.listarTodos().subscribe({
      next: (data: Cliente[]) => this.clientes.set(data),
      error: () => this.errorMessage.set('No se pudieron cargar los clientes.'),
    });
    this.productoService.listarTodos().subscribe({
      next: (data) => this.productos.set(data),
      error: () => this.errorMessage.set('No se pudieron cargar los productos.'),
    });
  }

  /**
   * Una línea del detalle. `busqueda` solo sirve al autocomplete y NO se envía:
   * al backend viajan únicamente productoId y cantidad.
   */
  private crearLinea() {
    return this.fb.nonNullable.group({
      busqueda: [''],
      productoId: [0, [Validators.min(1)]],
      cantidad: [1, [Validators.required, Validators.min(1)]],
    });
  }

  agregarLinea() {
    this.detalles.push(this.crearLinea());
  }

  quitarLinea(i: number) {
    if (this.detalles.length > 1) {
      this.detalles.removeAt(i);
      this.lineaAbierta.set(-1);
    }
  }

  // ── Autocomplete ────────────────────────────────────────────

  sugerencias(i: number): Producto[] {
    const texto = (this.lineas()[i]?.busqueda ?? '').trim().toLowerCase();
    if (!texto) return [];
    return this.productos()
      .filter((p) => p.nombre.toLowerCase().includes(texto))
      .slice(0, MAX_SUGERENCIAS);
  }

  /** Al escribir se descarta la selección anterior: hay que volver a elegir de la lista. */
  onBuscar(i: number) {
    this.detalles.at(i).controls.productoId.setValue(0);
    this.lineaAbierta.set(i);
  }

  elegirProducto(i: number, producto: Producto) {
    this.detalles.at(i).patchValue({ productoId: producto.id, busqueda: producto.nombre });
    this.lineaAbierta.set(-1);
  }

  cerrarSugerencias(i: number) {
    // Espera al click sobre la sugerencia antes de cerrar la lista
    setTimeout(() => {
      if (this.lineaAbierta() === i) this.lineaAbierta.set(-1);
    }, 150);
  }

  precio(p: Producto): number {
    return precioVenta(p);
  }

  controlaStock(p: Producto | undefined): boolean {
    return p?.sector === 'MATERIAL_SERVICIO';
  }

  /** Aviso anticipado; la validación definitiva de stock la hace el backend (409). */
  excedeStock(i: number): boolean {
    const p = this.productosPorLinea()[i];
    return this.controlaStock(p) && Number(this.lineas()[i]?.cantidad) > Number(p?.stockActual ?? 0);
  }

  // ── Guardar ─────────────────────────────────────────────────

  confirmarYGuardar() {
    this.errorMessage.set('');
    if (this.form.invalid || this.monedaMixta()) {
      this.form.markAllAsTouched();
      if (this.monedaMixta()) {
        this.errorMessage.set('No se pueden mezclar productos en soles y en dólares en una misma venta.');
      }
      return;
    }

    const valor = this.form.getRawValue();
    const cliente = this.clientes().find((c) => c.id === Number(valor.clienteId));
    const lineasTexto = this.productosPorLinea()
      .map((p, i) => `  • ${p?.nombre} × ${valor.detalles[i].cantidad}`)
      .join('\n');
    const resumen =
      `Vas a registrar una venta para ${cliente?.nombreORazonSocial}:\n\n${lineasTexto}\n\n` +
      `${this.detalles.length} línea(s) — Total aprox. ${this.simbolo()} ${this.total().toFixed(2)} (IGV incluido)\n` +
      `Pago: ${valor.tipoPago === 'CREDITO' ? `crédito a ${valor.diasCredito} días` : 'contado'}\n\n` +
      `Se descontará el stock de los materiales. ¿Confirmar?`;
    if (!confirm(resumen)) return;

    this.guardar();
  }

  private guardar() {
    const valor = this.form.getRawValue();
    const payload: VentaDirectaRequest = {
      clienteId: Number(valor.clienteId),
      items: valor.detalles.map((l) => ({ productoId: Number(l.productoId), cantidad: Number(l.cantidad) })),
      tipoPago: valor.tipoPago,
      diasCredito: valor.tipoPago === 'CREDITO' ? Number(valor.diasCredito) : undefined,
    };

    this.loading.set(true);
    this.ventaService.registrarVentaDirecta(payload).subscribe({
      next: () => {
        this.loading.set(false);
        this.router.navigate(['/ventas']);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.errorMessage.set(this.mensajeDeError(err));
      },
    });
  }

  /** Un mensaje distinto por cada error real que devuelve el backend. */
  private mensajeDeError(err: HttpErrorResponse): string {
    const detalle = err.error?.message ?? '';
    switch (err.status) {
      case 0:
        return 'No se pudo conectar con el servidor. ¿Está el backend encendido?';
      case 400:
        return `Datos inválidos: ${detalle}`;
      case 404:
        return `El cliente o un producto ya no existe (quizá se eliminó mientras llenabas el formulario). ${detalle}`;
      case 409:
        return `No hay stock suficiente: ${detalle}. Ajusta la cantidad e inténtalo de nuevo.`;
      default:
        return 'Error inesperado al registrar la venta. Inténtalo de nuevo.';
    }
  }
}
