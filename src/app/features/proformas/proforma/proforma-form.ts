import { Component, OnInit, inject, signal } from '@angular/core';
import { Moneda, TipoCambioService, convertir, simboloMoneda } from '../../../core/services/tipo-cambio-service';
import { DescuentoService } from '../../../core/services/descuento-service';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { AbstractControl, FormBuilder, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ProformaService } from './proforma-service';
import { ClienteService } from '../../clientes/cliente/cliente-service';
import { ProductoService } from '../../catalogo/producto/producto-service';
import { ProformaRequest } from './proforma.model';
import { Cliente } from '../../clientes/cliente/cliente.model';
import { Producto, precioVenta } from '../../catalogo/producto/producto.model';
import { AuthService } from '../../../core/auth/auth.service';
import { avisoUltimaCompra, errorPrecioEditado } from '../../ventas/venta/precio-editado';
import { EditarPrecioComponent, PrecioElegido } from '../../ventas/venta/editar-precio';
import { IconoComponent } from '../../../core/layout/icono';
import { ClienteFormComponent } from '../../clientes/cliente/cliente-form';

/** Una línea de la proforma. */
type ItemForm = FormGroup<{
  productoId: FormControl<number>;
  cantidad: FormControl<number>;
  descuentoPorcentaje: FormControl<number>;
  /** Precio editado (auditoría M1) en la moneda de la proforma; null = el de catálogo. */
  precioEditado: FormControl<number | null>;
  motivoPrecio: FormControl<string>;
}>;

/** Formulario reactivo cabecera-detalle (como VentaForm, S9): los ítems son un FormArray. */
@Component({
  selector: 'app-proforma-form',
  standalone: true,
  imports: [IconoComponent, CommonModule, ReactiveFormsModule, RouterLink, ClienteFormComponent, EditarPrecioComponent],
  templateUrl: './proforma-form.html'
})
export class ProformaFormComponent implements OnInit {
  private fb = inject(FormBuilder);
  private proformaService = inject(ProformaService);
  private clienteService = inject(ClienteService);
  private productoService = inject(ProductoService);
  private tipoCambioService = inject(TipoCambioService);
  private descuentoService = inject(DescuentoService);
  /** Descuento máximo por línea (%) del usuario actual. */
  topeDescuento = signal(0);
  avisoTipoCambio = signal<string>('');
  private router = inject(Router);
  private auth = inject(AuthService);

  /** Subir el precio de una línea (auditoría M1): el administrador o con el permiso "Editar precio en la venta". */
  readonly puedeEditarPrecio = () => this.auth.puede('EDITAR_PRECIO');
  /** Línea cuyo diálogo "Editar precio" está abierto (-1 = ninguno). */
  readonly lineaEditandoPrecio = signal(-1);

  clientes = signal<Cliente[]>([]);
  productos = signal<Producto[]>([]);
  errorMessage = signal<string>('');
  loading = signal<boolean>(false);
  /** Diálogo "Nuevo cliente": registrar al cliente sin salir de la proforma. */
  creandoCliente = signal(false);

  /** Descuento entre 0 y el tope del usuario (el tope llega del backend después de crear el formulario). */
  private readonly dentroDelTope = (control: AbstractControl): ValidationErrors | null => {
    const d = control.value ?? 0;
    return d < 0 || d > this.topeDescuento() ? { fueraDeTope: true } : null;
  };

  readonly form = this.fb.nonNullable.group({
    clienteId: [null as number | null, [Validators.required]],
    moneda: ['SOLES'],
    diasVigencia: [15, [Validators.min(1)]],
    tipoCambio: [null as number | null, [Validators.min(1)]],
    items: this.fb.array<ItemForm>([], [Validators.minLength(1)]),
  });

  /** Selector de la fila "+ Agregar Ítem" (no se envía: solo arma las líneas). */
  readonly nuevoItem = this.fb.nonNullable.group({
    productoId: [null as number | null],
    cantidad: [1, [Validators.min(1)]],
  });

  get items() {
    return this.form.controls.items;
  }

  private crearItem(productoId: number, cantidad: number): ItemForm {
    return this.fb.nonNullable.group({
      productoId: [productoId],
      cantidad: [cantidad, [Validators.min(1)]],
      descuentoPorcentaje: [0, [this.dentroDelTope]],
      precioEditado: [null as number | null],
      motivoPrecio: [''],
    });
  }

  // ── Precio de cada línea y "Editar precio" (auditoría M1) ──

  producto(id: number): Producto | undefined {
    return this.productos().find((p) => p.id === id);
  }

  simbolo(): string {
    return simboloMoneda(this.form.controls.moneda.value);
  }

  /** Precio de catálogo en la moneda de la proforma (null si falta el tipo de cambio). */
  precioCatalogo(i: number): number | null {
    const p = this.producto(this.items.at(i).controls.productoId.value);
    if (!p) return null;
    return convertir(precioVenta(p), p.moneda ?? 'SOLES', this.form.controls.moneda.value as Moneda,
      this.form.controls.tipoCambio.value);
  }

  /** El precio que se cotiza: el editado o el de catálogo. */
  precioLinea(i: number): number | null {
    const editado = this.items.at(i).controls.precioEditado.value;
    return editado !== null ? editado : this.precioCatalogo(i);
  }

  precioEditado(i: number): boolean {
    return this.items.at(i).controls.precioEditado.value !== null;
  }

  avisoCompra(i: number): string | null {
    const p = this.producto(this.items.at(i).controls.productoId.value);
    return p ? avisoUltimaCompra(p, precioVenta(p), simboloMoneda(p.moneda)) : null;
  }

  guardarPrecio(i: number, e: PrecioElegido) {
    this.items.at(i).patchValue({ precioEditado: e.precio, motivoPrecio: e.motivo });
    this.lineaEditandoPrecio.set(-1);
  }

  quitarPrecio(i: number) {
    this.items.at(i)?.patchValue({ precioEditado: null, motivoPrecio: '' });
    if (this.lineaEditandoPrecio() === i) this.lineaEditandoPrecio.set(-1);
  }

  ngOnInit() {
    this.descuentoService.tope().subscribe({ next: (t) => this.topeDescuento.set(t) });
    this.clienteService.listarTodos().subscribe({
      next: (data) => this.clientes.set(data),
      error: (err: HttpErrorResponse) => console.error('Error al cargar clientes:', err)
    });
    this.productoService.listarTodos().subscribe({
      next: (data) => this.productos.set(data),
      error: (err: HttpErrorResponse) => console.error('Error al cargar productos:', err)
    });
    // Otra moneda: los precios editados (que estaban en la anterior) vuelven al de catálogo
    this.form.controls.moneda.valueChanges.subscribe(() => this.items.controls.forEach((_, i) => this.quitarPrecio(i)));
    // Tipo de cambio SUNAT del día (editable). Queda fijo en la proforma y se respeta al venderla.
    this.tipoCambioService.hoy().subscribe({
      next: (tc) => {
        this.form.controls.tipoCambio.setValue(tc.venta);
        this.avisoTipoCambio.set(`SUNAT ${tc.fecha}: venta ${tc.venta.toFixed(3)}`);
      },
      error: () => this.avisoTipoCambio.set('No se pudo obtener el de SUNAT: escríbalo a mano si hay productos en otra moneda.')
    });
  }

  agregarItem() {
    const { productoId, cantidad } = this.nuevoItem.getRawValue();
    if (!productoId || !cantidad || cantidad <= 0) {
      return;
    }

    const existente = this.items.controls.find(i => i.controls.productoId.value === productoId);
    if (existente) {
      existente.controls.cantidad.setValue(existente.controls.cantidad.value + cantidad);
    } else {
      this.items.push(this.crearItem(productoId, cantidad));
    }

    this.nuevoItem.reset({ productoId: null, cantidad: 1 });
  }

  eliminarItem(index: number) {
    this.items.removeAt(index);
    this.lineaEditandoPrecio.set(-1);
  }

  /**
   * Cliente recién registrado (o ya existente con ese RUC/DNI) desde el diálogo: se agrega a la lista
   * si no estaba, en orden alfabético, y queda seleccionado en la proforma.
   */
  clienteCreado(c: Cliente) {
    if (c.id && !this.clientes().some((x) => x.id === c.id)) {
      this.clientes.update((lista) => [...lista, c].sort((a, b) => a.nombreORazonSocial.localeCompare(b.nombreORazonSocial)));
    }
    this.form.controls.clienteId.setValue(c.id ?? null);
    this.creandoCliente.set(false);
  }

  getNombreProducto(id: number): string {
    const prod = this.productos().find(p => p.id === id);
    return prod ? prod.nombre : 'Producto #' + id;
  }

  /** El aviso de error está arriba del formulario: se sube para que se vea. */
  private mostrarError(msg: string) {
    this.errorMessage.set(msg);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  guardar() {
    this.errorMessage.set('');
    const v = this.form.getRawValue();

    if (!v.clienteId) {
      this.mostrarError('Debe seleccionar un cliente.');
      return;
    }
    if (v.items.length === 0) {
      this.mostrarError('Debe agregar al menos un ítem a la proforma.');
      return;
    }
    const fueraDeTope = this.items.controls.find(i => i.controls.descuentoPorcentaje.invalid);
    if (fueraDeTope) {
      this.mostrarError(`El descuento de "${this.getNombreProducto(fueraDeTope.controls.productoId.value)}" debe estar entre 0 % y ${this.topeDescuento()} %.`);
      return;
    }
    // El tipo de cambio pudo moverse después de editar: el precio editado tiene que seguir por encima del de catálogo
    for (let i = 0; i < this.items.length; i++) {
      const catalogo = this.precioCatalogo(i);
      const item = this.items.at(i).controls;
      if (this.precioEditado(i) && catalogo !== null) {
        const error = errorPrecioEditado(catalogo, item.precioEditado.value, item.motivoPrecio.value);
        if (error) {
          this.mostrarError(`${this.getNombreProducto(item.productoId.value)}: ${error}`);
          return;
        }
      }
    }

    const payload: ProformaRequest = {
      clienteId: v.clienteId,
      moneda: v.moneda,
      diasVigencia: v.diasVigencia || 15,
      tipoCambio: v.tipoCambio || null,
      items: v.items.map(i => ({
        productoId: i.productoId,
        cantidad: i.cantidad,
        descuentoPorcentaje: i.descuentoPorcentaje ?? 0,
        precioUnitario: i.precioEditado,
        motivoPrecio: i.precioEditado !== null ? i.motivoPrecio : null
      }))
    };

    this.loading.set(true);
    this.proformaService.crear(payload).subscribe({
      next: () => {
        this.loading.set(false);
        this.router.navigate(['/proformas']);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        console.error('Error al guardar proforma:', err);
        const msg = err.error?.message || err.message || 'Error al procesar la proforma en el servidor.';
        this.mostrarError(msg);
      }
    });
  }
}
