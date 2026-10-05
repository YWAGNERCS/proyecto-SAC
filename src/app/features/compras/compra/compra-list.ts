import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { CompraService } from './compra-service';
import { CompraResponse } from './compra.model';

@Component({
  selector: 'app-compra-list',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './compra-list.html'
})
export class CompraListComponent implements OnInit {
  private compraService = inject(CompraService);

  compras = signal<CompraResponse[]>([]);
  loading = signal(false);
  errorMessage = signal('');

  ngOnInit() {
    this.cargarCompras();
  }

  cargarCompras() {
    this.loading.set(true);
    this.errorMessage.set('');
    this.compraService.listar().subscribe({
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
