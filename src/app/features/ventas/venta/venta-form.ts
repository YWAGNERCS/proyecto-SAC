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
import { Moneda, TipoCambioService, convertir, simboloMoneda } from '../../../core/services/tipo-cambio-service';
import { DescuentoService } from '../../../core/services/descuento-service';
import { IconoComponent } from '../../../core/layout/icono';

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
  imports: [IconoComponent, CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './venta-form.html',
  styleUrl: './venta-form.css',
})
export class VentaFormComponent implements OnInit {
  private fb = inject(FormBuilder);
  private ventaService = inject(VentaService);
  private tipoCambioService = inject(TipoCambioService);
  private descuentoService = inject(DescuentoService);
  private clienteService = inject(ClienteService);
  private productoService = inject(ProductoService);
  private router = inject(Router);

  clientes = signal<Cliente[]>([]);
  productos = signal<Producto[]>([]);
  errorMessage = signal('');
  loading = signal(false);
  /** Descuento máximo por línea (%) del usuario actual (el backend lo vuelve a validar). */
  topeDescuento = signal(0);
  /** Índice de la línea cuyo autocomplete está abierto (-1 = ninguno). */
  lineaAbierta = signal(-1);

  readonly form = this.fb.nonNullable.group({
    clienteId: [0, [Validators.min(1)]],
    tipoPago: ['CONTADO' as 'CONTADO' | 'CREDITO'],
    diasCredito: [30, [Validators.min(1)]],
    moneda: ['SOLES' as Moneda],
    tipoCambio: [null as number | null, [Validators.min(1), Validators.max(10)]],
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

  private readonly moneda = toSignal(this.form.controls.moneda.valueChanges, {
    initialValue: this.form.controls.moneda.value,
  });
  private readonly tipoCambio = toSignal(this.form.controls.tipoCambio.valueChanges, {
    initialValue: this.form.controls.tipoCambio.value,
  });
  /** Tipo de cambio que propuso SUNAT hoy (para avisar si el vendedor lo cambió). */
  readonly tipoCambioSunat = signal<number | null>(null);
  readonly avisoTipoCambio = signal<string | null>(null);

  /** Precio unitario en la moneda de la venta (convertido si el producto está en la otra moneda). */
  readonly preciosPorLinea = computed(() =>
    this.productosPorLinea().map((p) =>
      p ? convertir(precioVenta(p), p.moneda ?? 'SOLES', this.moneda(), this.tipoCambio()) : null,
    ),
  );

  /** Vista previa: el subtotal real lo calcula el backend con el precio vigente al guardar. */
  readonly subtotales = computed(() =>
    this.lineas().map((l, i) =>
      (this.preciosPorLinea()[i] ?? 0) * Number(l.cantidad || 0) * (1 - Number(l.descuento || 0) / 100)),
  );
  readonly subtotal = computed(() => this.subtotales().reduce((suma, s) => suma + s, 0));
  readonly igv = computed(() => this.subtotal() * IGV);
  readonly total = computed(() => this.subtotal() + this.igv());

  readonly simbolo = computed(() => simboloMoneda(this.moneda()));
  /** Hay productos en otra moneda: sin tipo de cambio no se pueden convertir. */
  readonly requiereTipoCambio = computed(() =>
    this.productosPorLinea().some((p) => !!p && (p.moneda ?? 'SOLES') !== this.moneda()),
  );
  readonly faltaTipoCambio = computed(() => this.requiereTipoCambio() && !this.tipoCambio());

  readonly esCredito = computed(() => this.tipoPago() === 'CREDITO');

  ngOnInit() {
    this.descuentoService.tope().subscribe({ next: (t) => this.topeDescuento.set(t) });
    this.clienteService.listarTodos().subscribe({
      next: (data: Cliente[]) => this.clientes.set(data),
      error: () => this.errorMessage.set('No se pudieron cargar los clientes.'),
    });
    this.productoService.listarTodos().subscribe({
      next: (data) => this.productos.set(data),
      error: () => this.errorMessage.set('No se pudieron cargar los productos.'),
    });
    // Tipo de cambio SUNAT del día: se propone, pero el vendedor puede cambiarlo si lo negoció
    this.tipoCambioService.hoy().subscribe({
      next: (tc) => {
        this.tipoCambioSunat.set(tc.venta);
        this.form.controls.tipoCambio.setValue(tc.venta);
        this.avisoTipoCambio.set(`SUNAT ${tc.fecha}: compra ${tc.compra.toFixed(3)} · venta ${tc.venta.toFixed(3)}`);
      },
      error: () => this.avisoTipoCambio.set('No se pudo obtener el tipo de cambio de SUNAT: escríbalo a mano.'),
    });
  }

  /**
   * Una línea del detalle. `busqueda` solo sirve al autocomplete y NO se envía:
   * al backend viajan productoId, cantidad y el descuento (%) de la línea.
   */
  private crearLinea() {
    return this.fb.nonNullable.group({
      busqueda: [''],
      productoId: [0, [Validators.min(1)]],
      cantidad: [1, [Validators.required, Validators.min(1)]],
      descuento: [0, [Validators.min(0), Validators.max(100)]],
    });
  }

  /** El tope depende del usuario, por eso se revisa aparte del validador fijo. */
  excedeDescuento(i: number): boolean {
    return Number(this.lineas()[i]?.descuento || 0) > this.topeDescuento();
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

  /** Precio en la moneda del propio producto (para la lista de búsqueda). */
  precio(p: Producto): number {
    return precioVenta(p);
  }

  simboloDe(moneda: string | undefined): string {
    return simboloMoneda(moneda);
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
    if (this.lineas().some((_, i) => this.excedeDescuento(i))) {
      this.errorMessage.set(`El descuento por línea no puede superar ${this.topeDescuento()} %.`);
      return;
    }
    if (this.form.invalid || this.faltaTipoCambio()) {
      this.form.markAllAsTouched();
      if (this.faltaTipoCambio()) {
        this.errorMessage.set('Hay productos en otra moneda: ingrese el tipo de cambio para convertirlos.');
      }
      return;
    }

    const valor = this.form.getRawValue();
    const cliente = this.clientes().find((c) => c.id === Number(valor.clienteId));
    const lineasTexto = this.productosPorLinea()
      .map((p, i) => `  • ${p?.nombre} × ${valor.detalles[i].cantidad}`
        + (Number(valor.detalles[i].descuento) > 0 ? ` (−${valor.detalles[i].descuento} %)` : ''))
      .join('\n');
    const resumen =
      `Vas a registrar una venta para ${cliente?.nombreORazonSocial}:\n\n${lineasTexto}\n\n` +
      `${this.detalles.length} línea(s) — Total aprox. ${this.simbolo()} ${this.total().toFixed(2)} (IGV incluido)\n` +
      `Pago: ${valor.tipoPago === 'CREDITO' ? `crédito a ${valor.diasCredito} días` : 'contado'}` +
      ` en ${valor.moneda === 'DOLARES' ? 'dólares' : 'soles'}\n` +
      this.lineaTipoCambio(valor.tipoCambio) +
      `\n` +
      `Se descontará el stock de los materiales. ¿Confirmar?`;
    if (!confirm(resumen)) return;

    this.guardar();
  }

  /** Línea del resumen de confirmación; avisa si el tipo de cambio no es el de SUNAT. */
  private lineaTipoCambio(tc: number | null): string {
    if (!tc) return '';
    const sunat = this.tipoCambioSunat();
    const distinto = sunat && Number(tc) !== sunat ? ` (distinto al de SUNAT: ${sunat.toFixed(3)})` : '';
    return `Tipo de cambio: S/ ${Number(tc).toFixed(3)} por US$${distinto}\n`;
  }

  private guardar() {
    const valor = this.form.getRawValue();
    const payload: VentaDirectaRequest = {
      clienteId: Number(valor.clienteId),
      items: valor.detalles.map((l) => ({
        productoId: Number(l.productoId),
        cantidad: Number(l.cantidad),
        descuentoPorcentaje: Number(l.descuento || 0),
      })),
      tipoPago: valor.tipoPago,
      diasCredito: valor.tipoPago === 'CREDITO' ? Number(valor.diasCredito) : undefined,
      moneda: valor.moneda,
      tipoCambio: valor.tipoCambio ? Number(valor.tipoCambio) : null,
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
        return `No se pudo registrar: ${detalle}`;
      default:
        return 'Error inesperado al registrar la venta. Inténtalo de nuevo.';
    }
  }
}
