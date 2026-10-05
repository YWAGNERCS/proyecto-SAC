import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { CotizacionService } from './cotizacion-service';
import { ClienteService } from '../../clientes/cliente/cliente-service';
import { ProductoService } from '../../catalogo/producto/producto-service';
import { CotizacionRequest } from './cotizacion.model';
import { Cliente } from '../../clientes/cliente/cliente.model';
import { Producto } from '../../catalogo/producto/producto.model';

@Component({
  selector: 'app-cotizacion-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './cotizacion-form.html'
})
export class CotizacionFormComponent implements OnInit {
  private cotizacionService = inject(CotizacionService);
  private clienteService = inject(ClienteService);
  private productoService = inject(ProductoService);
  private router = inject(Router);

  clientes = signal<Cliente[]>([]);
  productos = signal<Producto[]>([]);
  errorMessage = signal<string>('');

  cotizacion: CotizacionRequest = {
    clienteId: null,
    moneda: 'PEN',
    diasVigencia: 15,
    items: []
  };

  // Variables temporales para agregar item
  productoSeleccionadoId: number | null = null;
  cantidadSeleccionada: number = 1;

  ngOnInit() {
    this.clienteService.listarTodos().subscribe({
      next: (data: any) => this.clientes.set(data)
    });
    this.productoService.listarTodos().subscribe({
      next: (data) => this.productos.set(data)
    });
  }

  agregarItem() {
    if (!this.productoSeleccionadoId || this.cantidadSeleccionada <= 0) {
      return;
    }
    
    // Verificar si el producto ya esta en la lista
    const itemExistente = this.cotizacion.items.find(i => i.productoId == this.productoSeleccionadoId);
    
    if (itemExistente) {
      itemExistente.cantidad += this.cantidadSeleccionada;
    } else {
      this.cotizacion.items.push({
        productoId: Number(this.productoSeleccionadoId),
        cantidad: this.cantidadSeleccionada
      });
    }

    // Limpiar seleccion
    this.productoSeleccionadoId = null;
    this.cantidadSeleccionada = 1;
  }

  eliminarItem(index: number) {
    this.cotizacion.items.splice(index, 1);
  }

  getNombreProducto(id: number): string {
    const prod = this.productos().find(p => p.id == id);
    return prod ? prod.nombre : 'Desconocido';
  }

  guardar() {
    if (!this.cotizacion.clienteId) {
      this.errorMessage.set('Debe seleccionar un cliente.');
      return;
    }
    if (this.cotizacion.items.length === 0) {
      this.errorMessage.set('Debe agregar al menos un ítem a la cotización.');
      return;
    }

    this.cotizacionService.crear(this.cotizacion).subscribe({
      next: () => {
        this.router.navigate(['/cotizaciones']);
      },
      error: (err: any) => {
        this.errorMessage.set(err.error?.message || 'Error de servidor.');
      }
    });
  }
}
