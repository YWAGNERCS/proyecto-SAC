import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { CompraService } from './compra-service';
import { ProveedorService } from '../../proveedores/proveedor/proveedor-service';
import { ProductoService } from '../../catalogo/producto/producto-service';
import { CompraRequest } from './compra.model';
import { Proveedor } from '../../proveedores/proveedor/proveedor.model';
import { Producto } from '../../catalogo/producto/producto.model';

/** Una línea de la compra. */
type DetalleForm = FormGroup<{
  productoId: FormControl<number>;
  cantidad: FormControl<number>;
  precioUnitarioCompra: FormControl<number>;
}>;

/** Formulario reactivo cabecera-detalle (como VentaForm, S9): las líneas son un FormArray. */
@Component({
  selector: 'app-compra-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './compra-form.html'
})
export class CompraFormComponent implements OnInit {
  private fb = inject(FormBuilder);
  private compraService = inject(CompraService);
  private proveedorService = inject(ProveedorService);
  private productoService = inject(ProductoService);
  private router = inject(Router);

  proveedores = signal<Proveedor[]>([]);
  productos = signal<Producto[]>([]);
  errorMessage = signal('');
  loading = signal(false);

  /** Hoy en hora local (toISOString usaría UTC y de noche daría el día siguiente). */
  readonly hoy = (() => {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  })();

  readonly form = this.fb.nonNullable.group({
    proveedorId: [null as number | null, [Validators.required]],
    fechaCompra: [this.hoy, [Validators.required]],
    numeroFactura: ['', [Validators.required, Validators.pattern(/^\s*[A-Za-z0-9]{4}-\d{1,8}\s*$/)]],
    moneda: ['SOLES' as 'SOLES' | 'DOLARES'],
    tipoPago: ['CONTADO' as 'CONTADO' | 'CREDITO'],
    diasCredito: [30, [Validators.min(1), Validators.max(365)]],
    detalles: this.fb.array<DetalleForm>([], [Validators.minLength(1)]),
  });

  /** Selector de la fila "+ Agregar" (no se envía: solo arma las líneas). */
  readonly nuevoDetalle = this.fb.nonNullable.group({
    productoId: [null as number | null],
    cantidad: [1, [Validators.min(1)]],
    precioUnitarioCompra: [0, [Validators.min(0.01)]],
  });

  get detalles() {
    return this.form.controls.detalles;
  }

  /** Valor de las líneas como señal, para que el total se recalcule solo. */
  private readonly lineas = toSignal(this.detalles.valueChanges, { initialValue: this.detalles.getRawValue() });
  private readonly moneda = toSignal(this.form.controls.moneda.valueChanges, { initialValue: this.form.controls.moneda.value });

  readonly total = computed(() =>
    this.lineas().reduce((suma, d) => suma + (d.cantidad ?? 0) * (d.precioUnitarioCompra ?? 0), 0));
  readonly prefijoMoneda = computed(() => (this.moneda() === 'SOLES' ? 'PEN ' : 'USD '));

  ngOnInit() {
    this.proveedorService.listarTodos().subscribe({
      next: (data) => this.proveedores.set(data),
      error: (err: HttpErrorResponse) => console.error('Error al cargar proveedores:', err)
    });
    this.productoService.listarTodos().subscribe({
      next: (data) => this.productos.set(data),
      error: (err: HttpErrorResponse) => console.error('Error al cargar productos:', err)
    });
  }

  agregarDetalle() {
    const { productoId, cantidad, precioUnitarioCompra } = this.nuevoDetalle.getRawValue();
    if (!productoId || !cantidad || cantidad <= 0 || !precioUnitarioCompra || precioUnitarioCompra <= 0) {
      return;
    }

    const existente = this.detalles.controls.find(d => d.controls.productoId.value === productoId);
    if (existente) {
      existente.patchValue({
        cantidad: existente.controls.cantidad.value + cantidad,
        precioUnitarioCompra,
      });
    } else {
      this.detalles.push(this.fb.nonNullable.group({
        productoId: [productoId],
        cantidad: [cantidad, [Validators.min(1)]],
        precioUnitarioCompra: [precioUnitarioCompra, [Validators.min(0.01)]],
      }));
    }

    this.nuevoDetalle.reset({ productoId: null, cantidad: 1, precioUnitarioCompra: 0 });
  }

  eliminarDetalle(index: number) {
    this.detalles.removeAt(index);
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

    if (!v.proveedorId) {
      this.mostrarError('Debe seleccionar un proveedor.');
      return;
    }
    if (v.detalles.length === 0) {
      this.mostrarError('Debe agregar al menos un producto a la compra.');
      return;
    }
    if (this.form.controls.numeroFactura.invalid) {
      this.mostrarError('Ingrese el N° de factura del proveedor con el formato SERIE-NÚMERO, por ejemplo F001-123.');
      return;
    }
    if (v.tipoPago === 'CREDITO' && (!v.diasCredito || v.diasCredito < 1 || v.diasCredito > 365)) {
      this.mostrarError('Una compra a crédito necesita los días de crédito (1 a 365).');
      return;
    }
    if (v.fechaCompra > this.hoy) {
      this.mostrarError('La fecha de compra no puede ser futura.');
      return;
    }

    const payload: CompraRequest = {
      proveedorId: v.proveedorId,
      fechaCompra: v.fechaCompra,
      numeroFactura: v.numeroFactura.trim(),
      moneda: v.moneda,
      total: this.total(),
      detalles: v.detalles,
      tipoPago: v.tipoPago,
      diasCredito: v.tipoPago === 'CREDITO' ? v.diasCredito : null,
    };

    this.loading.set(true);
    this.compraService.registrar(payload).subscribe({
      next: () => {
        this.loading.set(false);
        this.router.navigate(['/compras']);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        // 400 con detalle por campo, 409 si la factura ya estaba registrada
        const errores = err.error?.errores as Record<string, string> | undefined;
        const msg = errores ? Object.values(errores).join(' · ')
          : err.status === 0 ? 'No hay conexión con el servidor.'
          : err.error?.message || 'Error al registrar la compra.';
        this.mostrarError(msg);
      }
    });
  }
}
