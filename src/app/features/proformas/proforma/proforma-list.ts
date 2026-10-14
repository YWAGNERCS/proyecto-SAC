import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { ProformaService } from './proforma-service';
import { ClienteService } from '../../clientes/cliente/cliente-service';
import { VentaService } from '../../ventas/venta/venta-service';
import { ProformaResponse } from './proforma.model';
import { Cliente } from '../../clientes/cliente/cliente.model';

@Component({
  selector: 'app-proforma-list',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './proforma-list.html'
})
export class ProformaListComponent implements OnInit {
  private proformaService = inject(ProformaService);
  private clienteService = inject(ClienteService);
  private ventaService = inject(VentaService);
  private router = inject(Router);

  proformas = signal<ProformaResponse[]>([]);
  clientes = signal<Cliente[]>([]);
  accionEnCurso = signal<number | null>(null);

  ngOnInit() {
    this.cargarProformas();
    this.cargarClientes();
  }

  cargarProformas(clienteId?: number) {
    this.proformaService.listar(clienteId).subscribe({
      next: (data: any) => this.proformas.set(data)
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
    this.cargarProformas(clienteId);
  }

  enviar(id: number) {
    this.accionEnCurso.set(id);
    this.proformaService.enviar(id).subscribe({
      next: () => {
        this.accionEnCurso.set(null);
        this.cargarProformas();
      },
      error: () => this.accionEnCurso.set(null)
    });
  }

  aceptar(id: number) {
    this.accionEnCurso.set(id);
    this.proformaService.aceptar(id).subscribe({
      next: () => {
        this.accionEnCurso.set(null);
        this.cargarProformas();
      },
      error: () => this.accionEnCurso.set(null)
    });
  }

  rechazar(id: number) {
    this.accionEnCurso.set(id);
    this.proformaService.rechazar(id).subscribe({
      next: () => {
        this.accionEnCurso.set(null);
        this.cargarProformas();
      },
      error: () => this.accionEnCurso.set(null)
    });
  }

  convertirEnVenta(proformaId: number) {
    this.accionEnCurso.set(proformaId);
    this.ventaService.convertirDesdeProforma(proformaId, 'CONTADO').subscribe({
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

