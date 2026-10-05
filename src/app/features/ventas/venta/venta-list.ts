import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { VentaService } from './venta-service';
import { VentaResponse } from './venta.model';

@Component({
  selector: 'app-venta-list',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './venta-list.html'
})
export class VentaListComponent implements OnInit {
  private ventaService = inject(VentaService);

  ventas = signal<VentaResponse[]>([]);
  filtro = signal<'TODAS' | 'ATRASADAS'>('TODAS');
  loading = signal(false);

  ngOnInit() {
    this.cargarVentas();
  }

  cargarVentas() {
    this.loading.set(true);
    this.ventaService.listar().subscribe({
      next: (data) => {
        this.ventas.set(data);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  filtrarAtrasadas() {
    this.filtro.set('ATRASADAS');
    this.loading.set(true);
    this.ventaService.listarPagosAtrasados().subscribe({
      next: (data) => {
        this.ventas.set(data);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  mostrarTodas() {
    this.filtro.set('TODAS');
    this.cargarVentas();
  }

  marcarPagada(id: number) {
    this.ventaService.marcarComoPagada(id).subscribe({
      next: () => {
        if (this.filtro() === 'ATRASADAS') {
          this.filtrarAtrasadas();
        } else {
          this.cargarVentas();
        }
      }
    });
  }

  getEstadoClass(estado: string): string {
    switch (estado) {
      case 'PAGADO': return 'badge-success';
      case 'ATRASADO': return 'badge-danger';
      default: return 'badge-warning';
    }
  }

  getTipoPagoLabel(tipo: string): string {
    return tipo === 'CONTADO' ? 'Contado' : 'Crédito';
  }
}
