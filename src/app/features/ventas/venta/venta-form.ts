import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { VentaService } from './venta-service';
import { ClienteService } from '../../clientes/cliente/cliente-service';
import { ProductoService } from '../../catalogo/producto/producto-service';
import { VentaDirectaRequest } from './venta.model';
import { Cliente } from '../../clientes/cliente/cliente.model';
import { Producto } from '../../catalogo/producto/producto.model';

@Component({
  selector: 'app-venta-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './venta-form.html'
})
export class VentaFormComponent implements OnInit {
  private ventaService = inject(VentaService);
  private clienteService = inject(ClienteService);
  private productoService = inject(ProductoService);
  private router = inject(Router);

  clientes = signal<Cliente[]>([]);
  productos = signal<Producto[]>([]);
  errorMessage = signal('');
  loading = signal(false);

  venta: VentaDirectaRequest = {
    clienteId: 0,
    items: [],
    tipoPago: 'CONTADO',
    diasCredito: undefined
  };

  clienteIdSeleccionado: number | null = null;
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
    if (!this.productoSeleccionadoId || this.cantidadSeleccionada <= 0) return;

    const prodId = Number(this.productoSeleccionadoId);
    const cant = Number(this.cantidadSeleccionada);
    const existente = this.venta.items.find(i => i.productoId === prodId);

    if (existente) {
      existente.cantidad += cant;
    } else {
      this.venta.items.push({ productoId: prodId, cantidad: cant });
    }

    this.productoSeleccionadoId = null;
    this.cantidadSeleccionada = 1;
  }

  eliminarItem(index: number) {
    this.venta.items.splice(index, 1);
  }

  getNombreProducto(id: number): string {
    const prod = this.productos().find(p => p.id === id);
    return prod ? prod.nombre : 'Producto #' + id;
  }

  onTipoPagoChange() {
    if (this.venta.tipoPago === 'CONTADO') {
      this.venta.diasCredito = undefined;
    } else {
      this.venta.diasCredito = 30;
    }
  }

  guardar() {
    this.errorMessage.set('');

    if (!this.clienteIdSeleccionado) {
      this.errorMessage.set('Debe seleccionar un cliente.');
      return;
    }
    if (!this.venta.items || this.venta.items.length === 0) {
      this.errorMessage.set('Debe agregar al menos un producto a la venta.');
      return;
    }

    const payload: VentaDirectaRequest = {
      clienteId: Number(this.clienteIdSeleccionado),
      items: this.venta.items.map(i => ({
        productoId: Number(i.productoId),
        cantidad: Number(i.cantidad)
      })),
      tipoPago: this.venta.tipoPago,
      diasCredito: this.venta.tipoPago === 'CREDITO' ? Number(this.venta.diasCredito || 30) : undefined
    };

    this.loading.set(true);
    this.ventaService.registrarVentaDirecta(payload).subscribe({
      next: () => {
        this.loading.set(false);
        this.router.navigate(['/ventas']);
      },
      error: (err: any) => {
        this.loading.set(false);
        const msg = err.error?.message || err.message || 'Error al registrar la venta.';
        this.errorMessage.set(msg);
      }
    });
  }
}
