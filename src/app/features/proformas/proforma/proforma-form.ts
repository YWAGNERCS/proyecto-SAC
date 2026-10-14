import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ProformaService } from './proforma-service';
import { ClienteService } from '../../clientes/cliente/cliente-service';
import { ProductoService } from '../../catalogo/producto/producto-service';
import { ProformaRequest } from './proforma.model';
import { Cliente } from '../../clientes/cliente/cliente.model';
import { Producto } from '../../catalogo/producto/producto.model';

@Component({
  selector: 'app-proforma-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './proforma-form.html'
})
export class ProformaFormComponent implements OnInit {
  private proformaService = inject(ProformaService);
  private clienteService = inject(ClienteService);
  private productoService = inject(ProductoService);
  private router = inject(Router);

  clientes = signal<Cliente[]>([]);
  productos = signal<Producto[]>([]);
  errorMessage = signal<string>('');
  loading = signal<boolean>(false);

  proforma: ProformaRequest = {
    clienteId: null,
    moneda: 'SOLES',
    diasVigencia: 15,
    items: []
  };

  productoSeleccionadoId: number | null = null;
  cantidadSeleccionada: number = 1;

  ngOnInit() {
    this.clienteService.listarTodos().subscribe({
      next: (data: any) => this.clientes.set(data),
      error: (err: any) => console.error('Error al cargar clientes:', err)
    });
    this.productoService.listarTodos().subscribe({
      next: (data) => this.productos.set(data),
      error: (err: any) => console.error('Error al cargar productos:', err)
    });
  }

  agregarItem() {
    if (!this.productoSeleccionadoId || this.cantidadSeleccionada <= 0) {
      return;
    }
    
    const prodId = Number(this.productoSeleccionadoId);
    const cant = Number(this.cantidadSeleccionada);
    const itemExistente = this.proforma.items.find(i => i.productoId === prodId);
    
    if (itemExistente) {
      itemExistente.cantidad += cant;
    } else {
      this.proforma.items.push({
        productoId: prodId,
        cantidad: cant
      });
    }

    this.productoSeleccionadoId = null;
    this.cantidadSeleccionada = 1;
  }

  eliminarItem(index: number) {
    this.proforma.items.splice(index, 1);
  }

  getNombreProducto(id: number): string {
    const prod = this.productos().find(p => p.id === id);
    return prod ? prod.nombre : 'Producto #' + id;
  }

  guardar() {
    this.errorMessage.set('');

    if (!this.proforma.clienteId) {
      this.errorMessage.set('Debe seleccionar un cliente.');
      return;
    }
    if (!this.proforma.items || this.proforma.items.length === 0) {
      this.errorMessage.set('Debe agregar al menos un ítem a la proforma.');
      return;
    }

    const payload = {
      clienteId: Number(this.proforma.clienteId),
      moneda: this.proforma.moneda,
      diasVigencia: Number(this.proforma.diasVigencia || 15),
      items: this.proforma.items.map(i => ({
        productoId: Number(i.productoId),
        cantidad: Number(i.cantidad)
      }))
    };

    this.loading.set(true);
    this.proformaService.crear(payload as any).subscribe({
      next: () => {
        this.loading.set(false);
        this.router.navigate(['/proformas']);
      },
      error: (err: any) => {
        this.loading.set(false);
        console.error('Error al guardar proforma:', err);
        const msg = err.error?.message || err.message || 'Error al procesar la proforma en el servidor.';
        this.errorMessage.set(msg);
      }
    });
  }
}