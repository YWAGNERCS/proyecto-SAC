import { Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MantenimientoService } from '../../postventa/mantenimiento/mantenimiento-service';
import { Mantenimiento, ResumenPostventa } from '../../postventa/mantenimiento/mantenimiento.model';
import { ClienteService } from '../../clientes/cliente/cliente-service';
import { Cliente } from '../../clientes/cliente/cliente.model';
import { IconoComponent } from '../../../core/layout/icono';

interface DiaMes {
  fecha: string;
  numero: number;
  esMesActual: boolean;
  hoy: boolean;
}

/**
 * Servicios › Calendario de Mantenimientos:
 * Muestra las visitas de mantenimiento preventivo organizadas por el MES COMPLETO.
 * Permite ver qué mantenimientos tocan cada día, ocultar/mostrar cancelados mediante interruptor,
 * y designar técnicos rápidamente creando la Orden de Trabajo.
 */
@Component({
  selector: 'app-agenda-mantenimientos',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, IconoComponent],
  templateUrl: './agenda-mantenimientos.html',
  styleUrl: './agenda-mantenimientos.css',
})
export class AgendaMantenimientosComponent {
  private readonly mantenimientoService = inject(MantenimientoService);
  private readonly clienteService = inject(ClienteService);
  private readonly router = inject(Router);

  /** Fecha de referencia para el mes visible (año y mes). */
  readonly fechaMes = signal(new Date());

  readonly mantenimientos = signal<Mantenimiento[]>([]);
  readonly resumen = signal<ResumenPostventa | null>(null);
  readonly clientes = signal<Cliente[]>([]);
  readonly clienteFiltro = signal<number | null>(null);

  /** Interruptor tipo switch: por defecto NO muestra cancelados para mantener limpio el mes. */
  readonly mostrarCancelados = signal(false);

  readonly cargando = signal(false);
  readonly error = signal('');

  readonly nombresDiasSemana = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'];

  /** Título del mes en español (ej. "Noviembre 2026"). */
  readonly tituloMes = computed(() => {
    const d = this.fechaMes();
    const str = d.toLocaleDateString('es-PE', { month: 'long', year: 'numeric' });
    return str.charAt(0).toUpperCase() + str.slice(1);
  });

  /** Días que componen la cuadrícula mensual (incluye relleno de mes anterior y posterior). */
  readonly diasCuadricula = computed<DiaMes[]>(() => {
    const d = this.fechaMes();
    const anio = d.getFullYear();
    const mes = d.getMonth();

    const hoyStr = this.iso(new Date());

    // Primer día del mes
    const primerDia = new Date(anio, mes, 1);
    // Día de la semana (0: Dom, 1: Lun, ..., 6: Sáb) convertido a 0: Lun ... 6: Dom
    const diaSemanaInicio = (primerDia.getDay() + 6) % 7;

    // Último día del mes
    const ultimoDia = new Date(anio, mes + 1, 0);
    const totalDiasMes = ultimoDia.getDate();

    const resultado: DiaMes[] = [];

    // Días del mes anterior para rellenar
    const ultimoDiaMesAnterior = new Date(anio, mes, 0).getDate();
    for (let i = diaSemanaInicio - 1; i >= 0; i--) {
      const fechaAnterior = new Date(anio, mes - 1, ultimoDiaMesAnterior - i);
      const isoStr = this.iso(fechaAnterior);
      resultado.push({
        fecha: isoStr,
        numero: fechaAnterior.getDate(),
        esMesActual: false,
        hoy: isoStr === hoyStr,
      });
    }

    // Días del mes actual
    for (let dia = 1; dia <= totalDiasMes; dia++) {
      const fechaActual = new Date(anio, mes, dia);
      const isoStr = this.iso(fechaActual);
      resultado.push({
        fecha: isoStr,
        numero: dia,
        esMesActual: true,
        hoy: isoStr === hoyStr,
      });
    }

    // Días del siguiente mes para completar las semanas (múltiplo de 7)
    const faltantes = (7 - (resultado.length % 7)) % 7;
    for (let i = 1; i <= faltantes; i++) {
      const fechaSiguiente = new Date(anio, mes + 1, i);
      const isoStr = this.iso(fechaSiguiente);
      resultado.push({
        fecha: isoStr,
        numero: i,
        esMesActual: false,
        hoy: isoStr === hoyStr,
      });
    }

    return resultado;
  });

  /** Mantenimientos filtrados por cliente y por el interruptor de cancelados. */
  readonly mantenimientosFiltrados = computed(() => {
    const cId = this.clienteFiltro();
    const verCancelados = this.mostrarCancelados();
    let lista = this.mantenimientos();

    if (!verCancelados) {
      lista = lista.filter((m) => m.estado !== 'CANCELADO');
    }
    if (cId) {
      lista = lista.filter((m) => m.clienteId === cId);
    }
    return lista;
  });

  /** Mantenimientos que caen en el mes visualizado. */
  readonly mantenimientosEnMes = computed(() => {
    const d = this.fechaMes();
    const anio = d.getFullYear();
    const mes = String(d.getMonth() + 1).padStart(2, '0');
    const prefijo = `${anio}-${mes}`;
    return this.mantenimientosFiltrados().filter((m) => m.fechaProgramada.startsWith(prefijo));
  });

  constructor() {
    this.clienteService.listarTodos().subscribe({
      next: (c) => this.clientes.set(c),
    });
    this.cargarResumen();
    this.cargar();
  }

  cargarResumen() {
    this.mantenimientoService.resumen().subscribe({
      next: (r) => this.resumen.set(r),
    });
  }

  cargar() {
    this.cargando.set(true);
    this.error.set('');
    // Listamos un rango amplio (ej. 365 días) en vista TODOS
    this.mantenimientoService.listar('TODOS', 365, this.clienteFiltro()).subscribe({
      next: (data) => {
        this.mantenimientos.set(data);
        this.cargando.set(false);
      },
      error: (err) => {
        this.error.set(err.error?.message ?? 'No se pudieron cargar los mantenimientos.');
        this.cargando.set(false);
      },
    });
  }

  moverMes(delta: number) {
    const actual = this.fechaMes();
    const nuevo = new Date(actual.getFullYear(), actual.getMonth() + delta, 1);
    this.fechaMes.set(nuevo);
  }

  irAHoy() {
    this.fechaMes.set(new Date());
  }

  alternarCancelados() {
    this.mostrarCancelados.update((v) => !v);
  }

  /** Visitas de una fecha específica. */
  visitasDelDia(fecha: string): Mantenimiento[] {
    return this.mantenimientosFiltrados()
      .filter((m) => m.fechaProgramada === fecha)
      .sort((a, b) => a.clienteNombre.localeCompare(b.clienteNombre));
  }

  /** Designar técnico / Programar orden de trabajo a partir del mantenimiento. */
  designarTecnico(m: Mantenimiento) {
    this.router.navigate(['/servicios/ordenes/nueva'], {
      queryParams: {
        mantenimientoId: m.id,
        clienteId: m.clienteId,
        fecha: m.fechaProgramada,
      },
    });
  }

  claseEstado(estado: string): string {
    switch (estado) {
      case 'PENDIENTE':
        return 'badge-pendiente';
      case 'REALIZADO':
        return 'badge-realizado';
      case 'CANCELADO':
        return 'badge-cancelado';
      default:
        return 'badge-secondary';
    }
  }

  private iso(d: Date) {
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }
}
