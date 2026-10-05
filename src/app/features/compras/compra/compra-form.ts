import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { CompraService } from './compra-service';
import { ProveedorService } from '../../proveedores/proveedor/proveedor-service';
import { ProductoService } from '../../catalogo/producto/producto-service';
import { CompraRequest, DetalleCompraRequest } from './compra.model';
import { Proveedor } from '../../proveedores/proveedor/proveedor.model';
import { Producto } from '../../catalogo/producto/producto.model';

@Component({
  selector: 'app-compra-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './compra-form.html'
})
export class CompraFormComponent implements OnInit {
  private compraService = inject(CompraService);
  private proveedorService = inject(ProveedorService);
  private productoService = inject(ProductoService);
  private router = inject(Router);

  proveedores = signal<Proveedor[]>([]);
  productos = signal<Producto[]>([]);
  errorMessage = signal('');
  loading = signal(false);

  proveedorIdSeleccionado: number | null = null;
  fechaCompra: string = new Date().toISOString().split('T')[0];
  numeroFactura: string = '';
  moneda: 'SOLES' | 'DOLARES' = 'SOLES';
  detalles: DetalleCompraRequest[] = [];

  productoSeleccionadoId: number | null = null;
  cantidadSeleccionada: number = 1;
  precioUnitarioSeleccionado: number = 0;

  ngOnInit() {
    this.proveedorService.listarTodos().subscribe({
      next: (data: any) => this.proveedores.set(data),
      error: (err: any) => console.error('Error al cargar proveedores:', err)
    });
    this.productoService.listarTodos().subscribe({
      next: (data) => this.productos.set(data),
      error: (err: any) => console.error('Error al cargar productos:', err)
    });
  }

  agregarDetalle() {
    if (!this.productoSeleccionadoId || this.cantidadSeleccionada <= 0 || this.precioUnitarioSeleccionado <= 0) {
      return;
    }

    const prodId = Number(this.productoSeleccionadoId);
    const cant = Number(this.cantidadSeleccionada);
    const precio = Number(this.precioUnitarioSeleccionado);

    const existente = this.detalles.find(d => d.productoId === prodId);
    if (existente) {
      existente.cantidad += cant;
      existente.precioUnitarioCompra = precio;
    } else {
      this.detalles.push({
        productoId: prodId,
        cantidad: cant,
        precioUnitarioCompra: precio
      });
    }

    this.productoSeleccionadoId = null;
    this.cantidadSeleccionada = 1;
    this.precioUnitarioSeleccionado = 0;
  }

  eliminarDetalle(index: number) {
    this.detalles.splice(index, 1);
  }

  getNombreProducto(id: number): string {
    const prod = this.productos().find(p => p.id === id);
    return prod ? prod.nombre : 'Producto #' + id;
  }

  calcularTotal(): number {
    return this.detalles.reduce((sum, d) => sum + (d.cantidad * d.precioUnitarioCompra), 0);
  }

  guardar() {
    this.errorMessage.set('');

    if (!this.proveedorIdSeleccionado) {
      this.errorMessage.set('Debe seleccionar un proveedor.');
      return;
    }
    if (!this.detalles || this.detalles.length === 0) {
      this.errorMessage.set('Debe agregar al menos un producto a la compra.');
      return;
    }

    const payload: CompraRequest = {
      proveedorId: Number(this.proveedorIdSeleccionado),
      fechaCompra: this.fechaCompra,
      numeroFactura: this.numeroFactura || undefined,
      moneda: this.moneda,
      total: this.calcularTotal(),
      detalles: this.detalles.map(d => ({
        productoId: Number(d.productoId),
        cantidad: Number(d.cantidad),
        precioUnitarioCompra: Number(d.precioUnitarioCompra)
      }))
    };

    this.loading.set(true);
    this.compraService.registrar(payload).subscribe({
      next: () => {
        this.loading.set(false);
        this.router.navigate(['/compras']);
      },
      error: (err: any) => {
        this.loading.set(false);
        const msg = err.error?.message || err.message || 'Error al registrar la compra.';
        this.errorMessage.set(msg);
      }
    });
  }
}
