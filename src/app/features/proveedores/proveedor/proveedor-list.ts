import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ProveedorService } from './proveedor-service';
import { Proveedor } from './proveedor.model';

@Component({
  selector: 'app-proveedor-list',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './proveedor-list.html',
  styleUrl: './proveedor-list.css',
})
export class ProveedorListComponent implements OnInit {
  proveedores = signal<Proveedor[]>([]);
  cargando = signal(true);
  error = signal<string | null>(null);
  busqueda = signal('');

  constructor(private proveedorService: ProveedorService) {}

  ngOnInit(): void {
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
      (p.correo ?? '').toLowerCase().includes(q)
    );
  }

  onBuscar(event: Event): void {
    this.busqueda.set((event.target as HTMLInputElement).value);
  }
}
