import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { OrdenService } from '../orden/orden-service';
import { OrdenTrabajo } from '../orden/orden.model';

interface Dia {
  fecha: string;
  nombre: string;
  numero: number;
  hoy: boolean;
}

/**
 * Servicios › Agenda: semana de lunes a domingo con una fila por técnico. Cada visita muestra la
 * hora, el cliente y el tipo; las que se cruzan en horario se marcan en rojo. Un clic en un espacio
 * vacío programa una orden nueva para ese técnico y ese día.
 */
@Component({
  selector: 'app-agenda',
  imports: [RouterLink],
  templateUrl: './agenda.html',
  styleUrl: './agenda.css',
})
export class AgendaComponent {
  private readonly ordenService = inject(OrdenService);
  private readonly router = inject(Router);

  /** Lunes de la semana que se ve. */
  readonly lunes = signal(this.lunesDe(new Date()));
  readonly ordenes = signal<OrdenTrabajo[]>([]);
  readonly cargando = signal(false);
  readonly error = signal('');

  private readonly nombres = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'];
  private readonly etiquetas: Record<string, string> = {
    INSTALACION: 'Instalación', MANTENIMIENTO: 'Mantenimiento', REPARACION: 'Reparación', VISITA_TECNICA: 'Visita técnica',
  };

  readonly dias = computed<Dia[]>(() => {
    const hoy = this.iso(new Date());
    return Array.from({ length: 7 }, (_, i) => {
      const d = new Date(this.lunes());
      d.setDate(d.getDate() + i);
      return { fecha: this.iso(d), nombre: this.nombres[i], numero: d.getDate(), hoy: this.iso(d) === hoy };
    });
  });

  readonly titulo = computed(() => {
    const d = this.dias();
    const fmt = (s: string) => new Date(s + 'T00:00').toLocaleDateString('es-PE', { day: 'numeric', month: 'short' });
    return `${fmt(d[0].fecha)} – ${fmt(d[6].fecha)} ${d[6].fecha.slice(0, 4)}`;
  });

  /** Técnicos de la semana, en orden alfabético. */
  readonly tecnicos = computed(() =>
    [...new Set(this.ordenes().map((o) => o.tecnico.trim()))].sort((a, b) => a.localeCompare(b)));

  /** Ids de las órdenes que se cruzan con otra del mismo técnico y día. */
  readonly cruces = computed(() => {
    const ids = new Set<number>();
    const programadas = this.ordenes().filter((o) => o.estado === 'PROGRAMADA' && o.horaInicio);
    for (const a of programadas) {
      for (const b of programadas) {
        if (a.id >= b.id || a.fechaProgramada !== b.fechaProgramada
          || a.tecnico.trim().toLowerCase() !== b.tecnico.trim().toLowerCase()) continue;
        const [ia, ib] = [this.minutos(a.horaInicio!), this.minutos(b.horaInicio!)];
        if (ia < ib + b.duracionMinutos && ib < ia + a.duracionMinutos) {
          ids.add(a.id);
          ids.add(b.id);
        }
      }
    }
    return ids;
  });

  readonly resumen = computed(() => {
    const o = this.ordenes();
    return { total: o.length, programadas: o.filter((x) => x.estado === 'PROGRAMADA').length, cruces: this.cruces().size };
  });

  constructor() {
    this.cargar();
  }

  cargar() {
    const d = this.dias();
    this.cargando.set(true);
    this.error.set('');
    this.ordenService.agenda(d[0].fecha, d[6].fecha).subscribe({
      next: (l) => {
        this.ordenes.set(l);
        this.cargando.set(false);
      },
      error: (err) => {
        this.error.set(err.error?.message ?? 'No se pudo cargar la agenda.');
        this.cargando.set(false);
      },
    });
  }

  mover(semanas: number) {
    const d = new Date(this.lunes());
    d.setDate(d.getDate() + semanas * 7);
    this.lunes.set(d);
    this.cargar();
  }

  irAHoy() {
    this.lunes.set(this.lunesDe(new Date()));
    this.cargar();
  }

  /** Visitas de un técnico en un día: primero las que tienen hora, en orden. */
  visitas(tecnico: string, fecha: string): OrdenTrabajo[] {
    return this.ordenes()
      .filter((o) => o.fechaProgramada === fecha && o.tecnico.trim() === tecnico)
      .sort((a, b) => (a.horaInicio ?? '99').localeCompare(b.horaInicio ?? '99'));
  }

  horario(o: OrdenTrabajo): string {
    if (!o.horaInicio) return 'En el día';
    const fin = this.minutos(o.horaInicio) + o.duracionMinutos;
    return `${o.horaInicio}–${String(Math.floor(fin / 60) % 24).padStart(2, '0')}:${String(fin % 60).padStart(2, '0')}`;
  }

  etiqueta(tipo: string) {
    return this.etiquetas[tipo] ?? tipo;
  }

  /** Programar en un espacio libre (no en días pasados). */
  programar(tecnico: string | null, dia: Dia) {
    if (dia.fecha < this.iso(new Date())) return;
    this.router.navigate(['/servicios/ordenes/nueva'], { queryParams: { fecha: dia.fecha, ...(tecnico ? { tecnico } : {}) } });
  }

  pasado(dia: Dia) {
    return dia.fecha < this.iso(new Date());
  }

  private minutos(hhmm: string) {
    return Number(hhmm.slice(0, 2)) * 60 + Number(hhmm.slice(3, 5));
  }

  private lunesDe(d: Date) {
    const r = new Date(d.getFullYear(), d.getMonth(), d.getDate());
    r.setDate(r.getDate() - ((r.getDay() + 6) % 7));
    return r;
  }

  private iso(d: Date) {
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }
}
