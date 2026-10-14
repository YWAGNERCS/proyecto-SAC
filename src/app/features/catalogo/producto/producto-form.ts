import { Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ProductoService } from './producto-service';
import { Moneda, ProductoRequest, SECTORES, Sector, controlaStock } from './producto.model';
import { CategoriaService } from '../categoria/categoria-service';
import { Categoria } from '../categoria/categoria.model';

@Component({
  selector: 'app-producto-form',
  imports: [ReactiveFormsModule, RouterLink, DecimalPipe],
  templateUrl: './producto-form.html',
})
export class ProductoForm {
  private readonly fb = inject(FormBuilder);
  private readonly productoService = inject(ProductoService);
  private readonly categoriaService = inject(CategoriaService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly sectores = SECTORES;
  protected readonly id = signal<number | null>(null);
  protected readonly categorias = signal<Categoria[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly loading = signal(false);
  protected readonly errorCarga = signal(false);

  protected readonly form = this.fb.group({
    nombre: this.fb.nonNullable.control('', [Validators.required, Validators.maxLength(150)]),
    descripcion: this.fb.nonNullable.control('', [Validators.maxLength(500)]),
    categoriaId: this.fb.control<number | null>(null, [Validators.required]),
    sector: this.fb.control<Sector | null>(null, [Validators.required]),
    moneda: this.fb.nonNullable.control<Moneda>('SOLES'),
    costoBase: this.fb.control<number | null>(null, [Validators.required, Validators.min(0)]),
    margenPorcentaje: this.fb.control<number | null>(25, [Validators.required, Validators.min(0)]),
    stockActual: this.fb.control<number | null>(0, [Validators.min(0)]),
    stockMinimo: this.fb.control<number | null>(0, [Validators.min(0)]),
    visibleEnCatalogo: this.fb.nonNullable.control(true),
  });

  private readonly valor = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });

  /** Vista previa: el backend calcula el mismo precio (costo + margen) al guardar. */
  protected readonly precioVenta = computed(() => {
    const v = this.valor();
    return Number(v.costoBase ?? 0) * (1 + Number(v.margenPorcentaje ?? 0) / 100);
  });
  protected readonly simbolo = computed(() => (this.valor().moneda === 'DOLARES' ? 'US$' : 'S/'));
  protected readonly esMaterial = computed(() => controlaStock(this.valor().sector));

  constructor() {
    this.categoriaService.listar().subscribe({
      next: (data) => this.categorias.set(data),
      error: () => this.error.set('No se pudieron cargar las categorías.'),
    });

    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      const id = Number(idParam);
      this.id.set(id);
      this.loading.set(true);
      this.productoService.obtener(id).subscribe({
        next: (p) => {
          this.form.patchValue({
            nombre: p.nombre,
            descripcion: p.descripcion ?? '',
            categoriaId: p.categoriaId ?? null,
            sector: p.sector ?? null,
            moneda: p.moneda ?? 'SOLES',
            costoBase: p.costoBase,
            margenPorcentaje: p.margenPorcentaje,
            stockActual: p.stockActual ?? 0,
            stockMinimo: p.stockMinimo ?? 0,
            visibleEnCatalogo: p.visibleEnCatalogo ?? true,
          });
          // Un producto antiguo sin categoría se marca para que el usuario la elija
          if (!p.categoriaId) this.form.controls.categoriaId.markAsTouched();
          this.loading.set(false);
        },
        error: () => {
          this.errorCarga.set(true);
          this.error.set('No se pudo cargar el producto.');
          this.loading.set(false);
        },
      });
    }
  }

  guardar(): void {
    if (this.loading() || this.errorCarga()) return;
    this.error.set(null);

    const nombre = this.form.controls.nombre;
    nombre.setValue(nombre.value.trim());
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const v = this.form.getRawValue();
    const material = controlaStock(v.sector);
    const request: ProductoRequest = {
      nombre: v.nombre,
      descripcion: v.descripcion,
      categoriaId: Number(v.categoriaId),
      sector: v.sector!,
      moneda: v.moneda,
      costoBase: Number(v.costoBase),
      margenPorcentaje: Number(v.margenPorcentaje),
      // Los equipos no controlan stock: se guarda en 0
      stockActual: material ? Number(v.stockActual ?? 0) : 0,
      stockMinimo: material ? Number(v.stockMinimo ?? 0) : 0,
      visibleEnCatalogo: v.visibleEnCatalogo,
    };

    const id = this.id();
    const peticion = id ? this.productoService.actualizar(id, request) : this.productoService.crear(request);
    this.loading.set(true);
    peticion.subscribe({
      next: () => this.router.navigate(['/catalogo/productos']),
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(this.mensajeDeError(err));
      },
    });
  }

  private mensajeDeError(err: HttpErrorResponse): string {
    const detalle = err.error?.message ?? '';
    switch (err.status) {
      case 0:
        return 'No se pudo conectar con el servidor.';
      case 400: {
        const errores = err.error?.errores as Record<string, string> | undefined;
        return errores ? `Datos inválidos: ${Object.values(errores).join(' · ')}` : `Datos inválidos: ${detalle}`;
      }
      case 404:
        return `${detalle}. Puede que se haya eliminado; recargue la página.`;
      default:
        return 'No se pudo guardar el producto.';
    }
  }

  protected mensajeValidacion(campo: keyof typeof this.form.controls): string {
    const control = this.form.controls[campo];
    if (!control.touched) return '';
    if (control.hasError('required')) return 'Este campo es obligatorio.';
    if (control.hasError('maxlength')) return `Máximo ${control.getError('maxlength').requiredLength} caracteres.`;
    if (control.hasError('min')) return 'No puede ser negativo.';
    return '';
  }
}
