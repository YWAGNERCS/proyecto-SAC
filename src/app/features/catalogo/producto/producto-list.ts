import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { ProductoService } from './producto-service';
import { Producto, SECTORES, controlaStock } from './producto.model';
import { CategoriaService } from '../categoria/categoria-service';
import { Categoria } from '../categoria/categoria.model';

/** Valor del filtro: todas, una categoría (id) o los productos aún sin categoría. */
type FiltroCategoria = 'TODAS' | 'SIN_CATEGORIA' | number;

@Component({
  selector: 'app-producto-list',
  imports: [RouterLink, FormsModule, DecimalPipe],
  templateUrl: './producto-list.html',
})
export class ProductoList implements OnInit {
  private readonly productoService = inject(ProductoService);
  private readonly categoriaService = inject(CategoriaService);

  protected readonly productos = signal<Producto[]>([]);
  protected readonly categorias = signal<Categoria[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly mensaje = signal<string | null>(null);
  protected readonly loading = signal(false);
  protected filtro: FiltroCategoria = 'TODAS';

  /** Con el filtro "sin categoría" se traen todos y se filtra aquí (el backend filtra por id). */
  protected readonly visibles = computed(() =>
    this.filtroActual() === 'SIN_CATEGORIA'
      ? this.productos().filter((p) => !p.categoriaId)
      : this.productos(),
  );
  protected readonly sinCategoria = signal(0);
  private readonly filtroActual = signal<FiltroCategoria>('TODAS');

  ngOnInit(): void {
    this.categoriaService.listar().subscribe({
      next: (data) => this.categorias.set(data),
      error: () => this.error.set('No se pudieron cargar las categorías.'),
    });
    this.cargar();
  }

  cargar(): void {
    this.loading.set(true);
    this.error.set(null);
    this.filtroActual.set(this.filtro);
    const categoriaId = typeof this.filtro === 'number' ? this.filtro : null;
    this.productoService.listar(categoriaId).subscribe({
      next: (data) => {
        this.productos.set(data);
        if (categoriaId === null) this.sinCategoria.set(data.filter((p) => !p.categoriaId).length);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('No se pudo cargar la lista de productos.');
        this.loading.set(false);
      },
    });
  }

  verSinCategoria(): void {
    this.filtro = 'SIN_CATEGORIA';
    this.cargar();
  }

  eliminar(p: Producto): void {
    if (!confirm(`¿Eliminar el producto "${p.nombre}"? Esta acción no se puede deshacer.`)) return;
    this.mensaje.set(null);
    this.productoService.eliminar(p.id).subscribe({
      next: () => {
        this.mensaje.set(`Producto "${p.nombre}" eliminado.`);
        this.cargar();
      },
      error: (err: HttpErrorResponse) => {
        // 409: el producto ya figura en ventas, proformas o compras
        this.error.set(err.status === 409 ? err.error?.message : 'No se pudo eliminar el producto.');
      },
    });
  }

  protected etiquetaSector(sector?: string): string {
    return SECTORES.find((s) => s.valor === sector)?.etiqueta ?? sector ?? '—';
  }

  protected simbolo(moneda?: string): string {
    return moneda === 'DOLARES' ? 'US$' : 'S/';
  }

  protected controlaStock = controlaStock;
}
