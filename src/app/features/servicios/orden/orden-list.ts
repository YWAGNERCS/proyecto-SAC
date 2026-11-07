import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { OrdenService } from './orden-service';
import { EstadoOrden, OrdenTrabajo, etiquetaTipo } from './orden.model';
import { ClienteService } from '../../clientes/cliente/cliente-service';
import { Cliente } from '../../clientes/cliente/cliente.model';
import { IconoComponent } from '../../../core/layout/icono';
import { CamposOrden, Orden, OrdenComponent, ordenarLista } from '../../../core/layout/orden';

/** Columnas ordenables con un clic en el encabezado (la lista llega completa: se ordena aquí). */
const CAMPOS_ORDEN: CamposOrden<OrdenTrabajo> = {
  id: (o) => o.id,
  fecha: (o) => `${o.fechaProgramada} ${o.horaInicio ?? ''}`,
  cliente: (o) => o.clienteNombre,
  trabajo: (o) => o.descripcion,
  tecnico: (o) => o.tecnico,
  estado: (o) => o.estado,
};

/** Órdenes de trabajo: agenda de los técnicos, con filtros por estado, cliente, técnico y fechas. */
@Component({
  selector: 'app-orden-list',
  imports: [IconoComponent, OrdenComponent, FormsModule, RouterLink],
  templateUrl: './orden-list.html',
})
export class OrdenListComponent implements OnInit {
  private readonly ordenService = inject(OrdenService);
  private readonly clienteService = inject(ClienteService);

  readonly ordenes = signal<OrdenTrabajo[]>([]);
  /** Columna por la que se ordena; null = el orden en que llegan del servidor. */
  readonly orden = signal<Orden | null>(null);
  readonly ordenesVisibles = computed(() => ordenarLista(this.ordenes(), this.orden(), CAMPOS_ORDEN));
  readonly clientes = signal<Cliente[]>([]);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly etiquetaTipo = etiquetaTipo;

  estado: EstadoOrden | '' = 'PROGRAMADA';
  clienteId: number | null = null;
  tecnico = '';
  desde = '';
  hasta = '';

  ngOnInit() {
    this.clienteService.listarTodos().subscribe({ next: (c) => this.clientes.set(c) });
    this.cargar();
  }

  cargar() {
    this.loading.set(true);
    this.error.set('');
    this.ordenService.listar({ estado: this.estado, clienteId: this.clienteId, tecnico: this.tecnico, desde: this.desde, hasta: this.hasta })
      .subscribe({
        next: (o) => { this.ordenes.set(o); this.loading.set(false); },
        error: (err: HttpErrorResponse) => {
          this.loading.set(false);
          this.error.set(err.error?.message ?? 'No se pudieron cargar las órdenes.');
        },
      });
  }

  limpiar() {
    this.estado = '';
    this.clienteId = null;
    this.tecnico = '';
    this.desde = '';
    this.hasta = '';
    this.cargar();
  }

  claseEstado(estado: EstadoOrden): string {
    return estado === 'REALIZADA' ? 'badge badge-success' : estado === 'CANCELADA' ? 'badge badge-danger' : 'badge badge-warning';
  }
}
