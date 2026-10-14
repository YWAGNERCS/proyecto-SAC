import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { ProveedorService } from './proveedor-service';
import { Proveedor } from './proveedor.model';

@Component({
  selector: 'app-proveedor-list',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './proveedor-list.html',
  styleUrl: './proveedor-list.css',
})
export class ProveedorListComponent implements OnInit {
  proveedores = signal<Proveedor[]>([]);
  cargando = signal(true);
  error = signal<string | null>(null);
  mensaje = signal<string | null>(null);
  busqueda = signal('');

  constructor(private proveedorService: ProveedorService) {}

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.cargando.set(true);
    this.proveedorService.listarTodos().subscribe({
      next: (data) => { this.proveedores.set(data); this.cargando.set(false); },
      error: () => { this.error.set('No se pudo cargar la lista de proveedores.'); this.cargando.set(false); },
    });
  }

  get proveedoresFiltrados(): Proveedor[] {
    const q = this.busqueda().toLowerCase();
    if (!q) return this.proveedores();
    return this.proveedores().filter(p =>
      p.nombreORazonSocial.toLowerCase().includes(q) ||
      (p.ruc ?? '').includes(q) ||
      (p.dni ?? '').includes(q) ||
      (p.correo ?? '').toLowerCase().includes(q)
    );
  }

  onBuscar(event: Event): void {
    this.busqueda.set((event.target as HTMLInputElement).value);
  }

  eliminar(p: Proveedor): void {
    if (!confirm(`¿Eliminar al proveedor "${p.nombreORazonSocial}"?`)) return;
    this.error.set(null);
    this.mensaje.set(null);
    this.proveedorService.eliminar(p.id!).subscribe({
      next: () => {
        this.mensaje.set(`Proveedor "${p.nombreORazonSocial}" eliminado.`);
        this.cargar();
      },
      error: (err: HttpErrorResponse) =>
        // 409: tiene compras registradas
        this.error.set(err.status === 409 ? err.error?.message : 'No se pudo eliminar el proveedor.'),
    });
  }
}
