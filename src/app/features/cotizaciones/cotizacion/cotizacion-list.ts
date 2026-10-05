import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { CotizacionService } from './cotizacion-service';
import { ClienteService } from '../../clientes/cliente/cliente-service';
import { VentaService } from '../../ventas/venta/venta-service';
import { CotizacionResponse } from './cotizacion.model';
import { Cliente } from '../../clientes/cliente/cliente.model';

@Component({
  selector: 'app-cotizacion-list',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './cotizacion-list.html'
})
export class CotizacionListComponent implements OnInit {
  private cotizacionService = inject(CotizacionService);
  private clienteService = inject(ClienteService);
  private ventaService = inject(VentaService);
  private router = inject(Router);

  cotizaciones = signal<CotizacionResponse[]>([]);
  clientes = signal<Cliente[]>([]);
  accionEnCurso = signal<number | null>(null);

  ngOnInit() {
    this.cargarCotizaciones();
    this.cargarClientes();
  }

  cargarCotizaciones(clienteId?: number) {
    this.cotizacionService.listar(clienteId).subscribe({
      next: (data: any) => this.cotizaciones.set(data)
    });
  }

  cargarClientes() {
    this.clienteService.listarTodos().subscribe({
      next: (data: any) => this.clientes.set(data)
    });
  }

  filtrarPorCliente(event: Event) {
    const select = event.target as HTMLSelectElement;
    const clienteId = select.value ? Number(select.value) : undefined;
    this.cargarCotizaciones(clienteId);
  }

  enviar(id: number) {
    this.accionEnCurso.set(id);
    this.cotizacionService.enviar(id).subscribe({
      next: () => {
        this.accionEnCurso.set(null);
        this.cargarCotizaciones();
      },
      error: () => this.accionEnCurso.set(null)
    });
  }

  aceptar(id: number) {
    this.accionEnCurso.set(id);
    this.cotizacionService.aceptar(id).subscribe({
      next: () => {
        this.accionEnCurso.set(null);
        this.cargarCotizaciones();
      },
      error: () => this.accionEnCurso.set(null)
    });
  }

  rechazar(id: number) {
    this.accionEnCurso.set(id);
    this.cotizacionService.rechazar(id).subscribe({
      next: () => {
        this.accionEnCurso.set(null);
        this.cargarCotizaciones();
      },
      error: () => this.accionEnCurso.set(null)
    });
  }

  convertirEnVenta(cotizacionId: number) {
    this.accionEnCurso.set(cotizacionId);
    this.ventaService.convertirDesdeCotizacion(cotizacionId, 'CONTADO').subscribe({
      next: () => {
        this.accionEnCurso.set(null);
        this.router.navigate(['/ventas']);
      },
      error: () => this.accionEnCurso.set(null)
    });
  }

  getEstadoClass(estado: string): string {
    switch (estado) {
      case 'ACEPTADA': return 'badge-success';
      case 'RECHAZADA': return 'badge-danger';
      case 'VENCIDA': return 'badge-warning';
      default: return '';
    }
  }
}

