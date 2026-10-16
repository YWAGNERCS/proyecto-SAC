import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CompraService } from './compra-service';
import { CompraResponse } from './compra.model';
import { DescargaService } from '../../../core/services/descarga-service';
import { ProveedorService } from '../../proveedores/proveedor/proveedor-service';
import { Proveedor } from '../../proveedores/proveedor/proveedor.model';

@Component({
  selector: 'app-compra-list',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule],
  templateUrl: './compra-list.html'
})
export class CompraListComponent implements OnInit {
  private compraService = inject(CompraService);
  private proveedorService = inject(ProveedorService);
  private descargaService = inject(DescargaService);

  compras = signal<CompraResponse[]>([]);
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
    this.cargarCompras();
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
    this.compraService.listar({ desde: this.desde, hasta: this.hasta, proveedorId: this.proveedorId }).subscribe({
      next: (data) => {
        this.compras.set(data);
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

  getTotalDetalles(compra: CompraResponse): number {
    return compra.detalles?.reduce((sum, d) => sum + d.cantidad, 0) || 0;
  }
}
