import { Component, OnInit, signal, inject } from '@angular/core';
import { AuthService } from '../../../core/auth/auth.service';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { ClienteService } from './cliente-service';
import { Cliente } from './cliente.model';

@Component({
  selector: 'app-cliente-list',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './cliente-list.html',
  styleUrl: './cliente-list.css',
})
export class ClienteListComponent implements OnInit {
  /** Solo ADMIN modifica/elimina/anula (el backend lo vuelve a exigir con 403). */
  readonly esAdmin = inject(AuthService).esAdmin;

  clientes = signal<Cliente[]>([]);
  cargando = signal(true);
  error = signal<string | null>(null);
  mensaje = signal<string | null>(null);
  busqueda = signal('');

  constructor(private clienteService: ClienteService) {}

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.cargando.set(true);
    this.clienteService.listarTodos().subscribe({
      next: (data) => { this.clientes.set(data); this.cargando.set(false); },
      error: () => { this.error.set('No se pudo cargar la lista de clientes.'); this.cargando.set(false); },
    });
  }

  /** RUC si es empresa, DNI si es persona natural. */
  documento(c: Cliente): string {
    return c.ruc ?? c.dni ?? '—';
  }

  get clientesFiltrados(): Cliente[] {
    const q = this.busqueda().toLowerCase();
    if (!q) return this.clientes();
    return this.clientes().filter(c =>
      c.nombreORazonSocial.toLowerCase().includes(q) ||
      (c.ruc ?? '').includes(q) ||
      (c.dni ?? '').includes(q) ||
      (c.correo ?? '').toLowerCase().includes(q)
    );
  }

  onBuscar(event: Event): void {
    this.busqueda.set((event.target as HTMLInputElement).value);
  }

  eliminar(c: Cliente): void {
    if (!confirm(`¿Eliminar al cliente "${c.nombreORazonSocial}"?`)) return;
    this.error.set(null);
    this.mensaje.set(null);
    this.clienteService.eliminar(c.id!).subscribe({
      next: () => {
        this.mensaje.set(`Cliente "${c.nombreORazonSocial}" eliminado.`);
        this.cargar();
      },
      error: (err: HttpErrorResponse) =>
        // 409: tiene proformas o ventas registradas
        this.error.set(err.status === 409 ? err.error?.message : 'No se pudo eliminar el cliente.'),
    });
  }
}
