import { Component, OnInit, inject, signal } from '@angular/core';
import { TipoCambioService } from '../../../core/services/tipo-cambio-service';
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
import { Producto } from '../../catalogo/producto/producto.model';
import { IconoComponent } from '../../../core/layout/icono';

/** Una línea de la proforma. */
type ItemForm = FormGroup<{
  productoId: FormControl<number>;
  cantidad: FormControl<number>;
  descuentoPorcentaje: FormControl<number>;
}>;

/** Formulario reactivo cabecera-detalle (como VentaForm, S9): los ítems son un FormArray. */
@Component({
  selector: 'app-proforma-form',
  standalone: true,
  imports: [IconoComponent, CommonModule, ReactiveFormsModule, RouterLink],
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

  clientes = signal<Cliente[]>([]);
  productos = signal<Producto[]>([]);
  errorMessage = signal<string>('');
  loading = signal<boolean>(false);

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
    });
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

    const payload: ProformaRequest = {
      clienteId: v.clienteId,
      moneda: v.moneda,
      diasVigencia: v.diasVigencia || 15,
      tipoCambio: v.tipoCambio || null,
      items: v.items.map(i => ({
        productoId: i.productoId,
        cantidad: i.cantidad,
        descuentoPorcentaje: i.descuentoPorcentaje ?? 0
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
