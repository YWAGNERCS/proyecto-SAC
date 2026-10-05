import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { CotizacionService } from './cotizacion-service';
import { ClienteService } from '../../clientes/cliente/cliente-service';
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

  cotizaciones = signal<CotizacionResponse[]>([]);
  clientes = signal<Cliente[]>([]);

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
}
