import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CompraService } from './compra-service';
import { CompraResponse } from './compra.model';
import { DescargaService } from '../../../core/services/descarga-service';
import { ProveedorService } from '../../proveedores/proveedor/proveedor-service';
import { Proveedor } from '../../proveedores/proveedor/proveedor.model';
import { IconoComponent } from '../../../core/layout/icono';
import { PaginadorComponent } from '../../../core/layout/paginador';
import { Orden, OrdenComponent, ordenParaServidor } from '../../../core/layout/orden';

@Component({
  selector: 'app-compra-list',
  standalone: true,
  imports: [PaginadorComponent, OrdenComponent, IconoComponent, CommonModule, RouterLink, FormsModule],
  templateUrl: './compra-list.html'
})
export class CompraListComponent implements OnInit {
  private compraService = inject(CompraService);
  private proveedorService = inject(ProveedorService);
  private descargaService = inject(DescargaService);

  compras = signal<CompraResponse[]>([]);
  /** Columna por la que se ordena (clic en el encabezado; la ordena el servidor sobre todas las páginas). */
  orden = signal<Orden | null>(null);
  pagina = signal(0);
  tamano = signal(20);
  total = signal(0);
  totalPaginas = signal(0);
  proveedores = signal<Proveedor[]>([]);
  loading = signal(false);
  exportando = signal(false);
  errorMessage = signal('');

  // Filtros: se aplican al pulsar "Buscar"
  desde = '';
  hasta = '';
  proveedorId: number | null = null;

  ngOnInit() {
    this.proveedorService.listarTodos().subscribe({ next: (data) => this.proveedores.set(data) });
    this.cargarCompras();
  }

  limpiar() {
    this.desde = '';
    this.hasta = '';
    this.proveedorId = null;
    this.buscar();
  }

  /** Excel con los mismos filtros de la pantalla (hojas "Compras" y "Detalle"). */
  exportarExcel() {
    this.exportando.set(true);
    this.descargaService
      .descargar('/v1/admin/compras/exportar', `compras_${this.descargaService.hoy()}.xlsx`,
        { desde: this.desde, hasta: this.hasta, proveedorId: this.proveedorId })
      .subscribe({
        next: () => this.exportando.set(false),
        error: () => {
          this.exportando.set(false);
          this.errorMessage.set('No se pudo generar el Excel de compras.');
        },
      });
  }

  cargarCompras() {
    if (this.desde && this.hasta && this.desde > this.hasta) {
      this.errorMessage.set('La fecha "desde" no puede ser posterior a "hasta".');
      return;
    }
    this.loading.set(true);
    this.errorMessage.set('');
    this.compraService.listarPagina({ desde: this.desde, hasta: this.hasta, proveedorId: this.proveedorId },
      this.pagina(), this.tamano(), ordenParaServidor(this.orden())).subscribe({
      next: (p) => {
        this.compras.set(p.contenido);
        this.total.set(p.total);
        this.totalPaginas.set(p.totalPaginas);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Error al cargar compras:', err);
        const msg = err.error?.message || err.message || 'Error al conectar con el servidor';
        this.errorMessage.set(msg);
        this.loading.set(false);
      }
    });
  }

  ordenar(o: Orden | null) {
    this.orden.set(o);
    this.pagina.set(0);
    this.cargarCompras();
  }

  /** "Buscar" y "Limpiar" vuelven a la primera página. */
  buscar() {
    this.pagina.set(0);
    this.cargarCompras();
  }

  irAPagina(p: number) {
    this.pagina.set(p);
    this.cargarCompras();
  }

  cambiarTamano(t: number) {
    this.tamano.set(t);
    this.pagina.set(0);
    this.cargarCompras();
  }

  getTotalDetalles(compra: CompraResponse): number {
    return compra.detalles?.reduce((sum, d) => sum + d.cantidad, 0) || 0;
  }
}
