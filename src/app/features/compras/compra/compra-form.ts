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
  /** Hoy en hora local (toISOString usaría UTC y de noche daría el día siguiente). */
  readonly hoy = (() => {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  })();
  fechaCompra: string = this.hoy;
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

  /** El aviso de error está arriba del formulario: se sube para que se vea. */
  private mostrarError(msg: string) {
    this.errorMessage.set(msg);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  guardar() {
    this.errorMessage.set('');

    if (!this.proveedorIdSeleccionado) {
      this.mostrarError('Debe seleccionar un proveedor.');
      return;
    }
    if (!this.detalles || this.detalles.length === 0) {
      this.mostrarError('Debe agregar al menos un producto a la compra.');
      return;
    }
    if (!/^[A-Za-z0-9]{4}-\d{1,8}$/.test(this.numeroFactura.trim())) {
      this.mostrarError('Ingrese el N° de factura del proveedor con el formato SERIE-NÚMERO, por ejemplo F001-123.');
      return;
    }
    if (this.fechaCompra > this.hoy) {
      this.mostrarError('La fecha de compra no puede ser futura.');
      return;
    }

    const payload: CompraRequest = {
      proveedorId: Number(this.proveedorIdSeleccionado),
      fechaCompra: this.fechaCompra,
      numeroFactura: this.numeroFactura.trim(),
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
        // 400 con detalle por campo, 409 si la factura ya estaba registrada
        const errores = err.error?.errores as Record<string, string> | undefined;
        const msg = errores ? Object.values(errores).join(' · ')
          : err.status === 0 ? 'No hay conexión con el servidor.'
          : err.error?.message || 'Error al registrar la compra.';
        this.mostrarError(msg);
      }
    });
  }
}
