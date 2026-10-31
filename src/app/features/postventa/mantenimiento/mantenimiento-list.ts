import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { MantenimientoService } from './mantenimiento-service';
import { Mantenimiento, ResumenPostventa, VistaPostventa } from './mantenimiento.model';
import { ClienteService } from '../../clientes/cliente/cliente-service';
import { Cliente } from '../../clientes/cliente/cliente.model';
import { IconoComponent } from '../../../core/layout/icono';

type Accion = 'REALIZADO' | 'REPROGRAMAR';

@Component({
  selector: 'app-mantenimiento-list',
  standalone: true,
  imports: [IconoComponent, CommonModule, FormsModule, RouterLink],
  templateUrl: './mantenimiento-list.html',
  styleUrl: './mantenimiento-list.css',
})
export class MantenimientoListComponent implements OnInit {
  private mantenimientoService = inject(MantenimientoService);
  private clienteService = inject(ClienteService);

  readonly vistas: { valor: VistaPostventa; etiqueta: string }[] = [
    { valor: 'VENCIDOS', etiqueta: 'Vencidos' },
    { valor: 'PROXIMOS', etiqueta: 'Próximos' },
    { valor: 'REALIZADOS', etiqueta: 'Realizados' },
    { valor: 'TODOS', etiqueta: 'Todos' },
  ];

  vista = signal<VistaPostventa>('PROXIMOS');
  dias = 30;
  clienteId: number | null = null;

  mantenimientos = signal<Mantenimiento[]>([]);
  resumen = signal<ResumenPostventa | null>(null);
  clientes = signal<Cliente[]>([]);
  loading = signal(false);
  error = signal('');
  mensaje = signal('');

  // Diálogo "Realizado" / "Reprogramar"
  seleccionado = signal<Mantenimiento | null>(null);
  accion = signal<Accion>('REALIZADO');
  fecha = '';
  notas = '';
  guardando = signal(false);
  errorDialogo = signal('');

  readonly hoy = this.isoHoy();

  ngOnInit() {
    this.clienteService.listarTodos().subscribe({
      next: (data: Cliente[]) => this.clientes.set(data),
    });
    this.cargarResumen();
    this.cargar();
  }

  cambiarVista(v: VistaPostventa) {
    this.vista.set(v);
    this.cargar();
  }

  cargar() {
    this.loading.set(true);
    this.error.set('');
    this.mantenimientoService.listar(this.vista(), Number(this.dias) || 30, this.clienteId).subscribe({
      next: (data) => {
        this.mantenimientos.set(data);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(this.mensajeDeError(err, 'No se pudo cargar la agenda de mantenimientos.'));
      },
    });
  }

  private cargarResumen() {
    this.mantenimientoService.resumen().subscribe({ next: (r) => this.resumen.set(r) });
  }

  // ── Diálogo ─────────────────────────────────────────────────

  abrir(m: Mantenimiento, accion: Accion) {
    this.seleccionado.set(m);
    this.accion.set(accion);
    this.fecha = accion === 'REALIZADO' ? this.hoy : '';
    this.notas = '';
    this.errorDialogo.set('');
  }

  cerrar() {
    if (!this.guardando()) this.seleccionado.set(null);
  }

  confirmar() {
    const m = this.seleccionado();
    if (!m) return;
    if (this.accion() === 'REPROGRAMAR' && !this.fecha) {
      this.errorDialogo.set('Elija la nueva fecha.');
      return;
    }
    const peticion = this.accion() === 'REALIZADO'
      ? this.mantenimientoService.marcarRealizado(m.id, this.fecha, this.notas)
      : this.mantenimientoService.reprogramar(m.id, this.fecha, this.notas);
    this.guardando.set(true);
    this.errorDialogo.set('');
    peticion.subscribe({
      next: (actualizado) => {
        this.guardando.set(false);
        this.seleccionado.set(null);
        this.mensaje.set(this.accion() === 'REALIZADO'
          ? `Mantenimiento de ${m.clienteNombre} marcado como realizado.`
          : `Mantenimiento de ${m.clienteNombre} reprogramado al ${actualizado.fechaProgramada}.`);
        this.cargarResumen();
        this.cargar();
      },
      error: (err: HttpErrorResponse) => {
        this.guardando.set(false);
        this.errorDialogo.set(this.mensajeDeError(err, 'No se pudo guardar el cambio.'));
      },
    });
  }

  // ── Ayudas de presentación ──────────────────────────────────

  textoDias(m: Mantenimiento): string {
    if (m.estado !== 'PENDIENTE') return '';
    const d = m.diasParaFecha;
    if (d === 0) return 'hoy';
    if (d === 1) return 'mañana';
    if (d > 0) return `en ${d} días`;
    return d === -1 ? 'venció ayer' : `venció hace ${-d} días`;
  }

  claseDias(m: Mantenimiento): string {
    if (m.estado !== 'PENDIENTE') return '';
    if (m.diasParaFecha < 0) return 'badge-danger';
    return m.diasParaFecha <= 7 ? 'badge-warning' : 'badge-info';
  }

  claseEstado(estado: string): string {
    switch (estado) {
      case 'REALIZADO': return 'badge-success';
      case 'CANCELADO': return 'badge-danger';
      default: return '';
    }
  }

  /** Enlace de WhatsApp (Perú: +51) para coordinar la visita. */
  whatsapp(m: Mantenimiento): string | null {
    const digitos = (m.clienteTelefono ?? '').replace(/\D/g, '');
    if (digitos.length < 9) return null;
    const numero = digitos.length === 9 ? `51${digitos}` : digitos;
    const texto = `Hola, le saluda JD Refrigeración. Le corresponde el mantenimiento preventivo de su equipo `
      + `(${m.equipos}) programado para el ${m.fechaProgramada}. ¿Qué día le acomoda para la visita?`;
    return `https://wa.me/${numero}?text=${encodeURIComponent(texto)}`;
  }

  private isoHoy(): string {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }

  private mensajeDeError(err: HttpErrorResponse, porDefecto: string): string {
    if (err.status === 0) return 'No hay conexión con el servidor. Revise que el backend esté encendido.';
    return err.error?.message ?? porDefecto;
  }
}
