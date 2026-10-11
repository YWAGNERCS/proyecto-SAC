import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ClienteService } from './cliente-service';
import { Cliente } from './cliente.model';

@Component({
  selector: 'app-cliente-list',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './cliente-list.html',
  styleUrl: './cliente-list.css',
})
export class ClienteListComponent implements OnInit {
  clientes = signal<Cliente[]>([]);
  cargando = signal(true);
  error = signal<string | null>(null);
  busqueda = signal('');

  constructor(private clienteService: ClienteService) {}

  ngOnInit(): void {
    this.clienteService.listarTodos().subscribe({
      next: (data) => { this.clientes.set(data); this.cargando.set(false); },
      error: () => { this.error.set('No se pudo cargar la lista de clientes.'); this.cargando.set(false); },
    });
  }

  get clientesFiltrados(): Cliente[] {
    const q = this.busqueda().toLowerCase();
    if (!q) return this.clientes();
    return this.clientes().filter(c =>
      c.nombreORazonSocial.toLowerCase().includes(q) ||
      c.nroDocumento.includes(q)
    );
  }

  onBuscar(event: Event): void {
    this.busqueda.set((event.target as HTMLInputElement).value);
  }
}
