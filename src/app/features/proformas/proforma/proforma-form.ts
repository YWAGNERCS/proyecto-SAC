import { Component, OnInit, inject, signal } from '@angular/core';
import { TipoCambioService } from '../../../core/services/tipo-cambio-service';
import { DescuentoService } from '../../../core/services/descuento-service';
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
  private tipoCambioService = inject(TipoCambioService);
  private descuentoService = inject(DescuentoService);
  /** Descuento máximo por línea (%) del usuario actual. */
  topeDescuento = signal(0);
  avisoTipoCambio = signal<string>('');
  private router = inject(Router);

  clientes = signal<Cliente[]>([]);
  productos = signal<Producto[]>([]);
  errorMessage = signal<string>('');
  loading = signal<boolean>(false);

  proforma: ProformaRequest = {
    clienteId: null,
    moneda: 'SOLES',
    diasVigencia: 15,
    items: [],
    tipoCambio: null
  };

  productoSeleccionadoId: number | null = null;
  cantidadSeleccionada: number = 1;

  ngOnInit() {
    this.descuentoService.tope().subscribe({ next: (t) => this.topeDescuento.set(t) });
    this.clienteService.listarTodos().subscribe({
      next: (data: any) => this.clientes.set(data),
      error: (err: any) => console.error('Error al cargar clientes:', err)
    });
    this.productoService.listarTodos().subscribe({
      next: (data) => this.productos.set(data),
      error: (err: any) => console.error('Error al cargar productos:', err)
    });
    // Tipo de cambio SUNAT del día (editable). Queda fijo en la proforma y se respeta al venderla.
    this.tipoCambioService.hoy().subscribe({
      next: (tc) => {
        this.proforma.tipoCambio = tc.venta;
        this.avisoTipoCambio.set(`SUNAT ${tc.fecha}: venta ${tc.venta.toFixed(3)}`);
      },
      error: () => this.avisoTipoCambio.set('No se pudo obtener el de SUNAT: escríbalo a mano si hay productos en otra moneda.')
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
        cantidad: cant,
        descuentoPorcentaje: 0
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

  /** El aviso de error está arriba del formulario: se sube para que se vea. */
  private mostrarError(msg: string) {
    this.errorMessage.set(msg);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  guardar() {
    this.errorMessage.set('');

    if (!this.proforma.clienteId) {
      this.mostrarError('Debe seleccionar un cliente.');
      return;
    }
    if (!this.proforma.items || this.proforma.items.length === 0) {
      this.mostrarError('Debe agregar al menos un ítem a la proforma.');
      return;
    }
    const fueraDeTope = this.proforma.items.find(i => {
      const d = Number(i.descuentoPorcentaje || 0);
      return d < 0 || d > this.topeDescuento();
    });
    if (fueraDeTope) {
      this.mostrarError(`El descuento de "${this.getNombreProducto(fueraDeTope.productoId)}" debe estar entre 0 % y ${this.topeDescuento()} %.`);
      return;
    }

    const payload = {
      clienteId: Number(this.proforma.clienteId),
      moneda: this.proforma.moneda,
      diasVigencia: Number(this.proforma.diasVigencia || 15),
      tipoCambio: this.proforma.tipoCambio ? Number(this.proforma.tipoCambio) : null,
      items: this.proforma.items.map(i => ({
        productoId: Number(i.productoId),
        cantidad: Number(i.cantidad),
        descuentoPorcentaje: Number(i.descuentoPorcentaje || 0)
      }))
    };

    this.loading.set(true);
    this.proformaService.crear(payload).subscribe({
      next: () => {
        this.loading.set(false);
        this.router.navigate(['/proformas']);
      },
      error: (err: any) => {
        this.loading.set(false);
        console.error('Error al guardar proforma:', err);
        const msg = err.error?.message || err.message || 'Error al procesar la proforma en el servidor.';
        this.mostrarError(msg);
      }
    });
  }
}