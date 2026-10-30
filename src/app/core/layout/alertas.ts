import { Component, DestroyRef, ElementRef, HostListener, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin, interval, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AuthService } from '../auth/auth.service';
import { ICONOS } from './menu';
import { ProductoService } from '../../features/catalogo/producto/producto-service';
import { Producto } from '../../features/catalogo/producto/producto.model';
import { MantenimientoService } from '../../features/postventa/mantenimiento/mantenimiento-service';
import { ResumenPostventa } from '../../features/postventa/mantenimiento/mantenimiento.model';
import { CobranzaService } from '../../features/cobranza/cobro/cobranza-service';
import { CuentaPorCobrar, Devolucion } from '../../features/cobranza/cobro/cobranza.model';
import { ProformaService } from '../../features/proformas/proforma/proforma-service';
import { ProformaResponse } from '../../features/proformas/proforma/proforma.model';
import { OrdenService } from '../../features/servicios/orden/orden-service';
import { OrdenTrabajo } from '../../features/servicios/orden/orden.model';

interface Alerta {
  nivel: 'alta' | 'media' | 'info';
  titulo: string;
  detalle: string;
  ruta: string;
  query?: Record<string, string>;
}

function isoLocal(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/**
 * Campana de la barra superior: lo que necesita atención hoy, cada aviso con enlace a donde se resuelve.
 * Se actualiza al abrirla y cada 5 minutos. Solo lee, con los mismos endpoints de cada pantalla.
 */
@Component({
  selector: 'app-alertas',
  imports: [RouterLink],
  templateUrl: './alertas.html',
  styleUrl: './alertas.css',
})
export class AlertasComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly productoService = inject(ProductoService);
  private readonly mantenimientoService = inject(MantenimientoService);
  private readonly cobranzaService = inject(CobranzaService);
  private readonly proformaService = inject(ProformaService);
  private readonly ordenService = inject(OrdenService);
  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly destroyRef = inject(DestroyRef);

  readonly iconoCampana = ICONOS.campana;
  readonly abierto = signal(false);
  readonly alertas = signal<Alerta[]>([]);
  readonly urgentes = computed(() => this.alertas().filter((a) => a.nivel !== 'info').length);

  ngOnInit() {
    this.cargar();
    interval(5 * 60 * 1000).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.cargar());
  }

  alternar() {
    this.abierto.update((v) => !v);
    if (this.abierto()) this.cargar();
  }

  @HostListener('document:click', ['$event'])
  clicAfuera(e: MouseEvent) {
    if (!this.host.nativeElement.contains(e.target as Node)) this.abierto.set(false);
  }

  @HostListener('document:keydown.escape')
  escape() {
    this.abierto.set(false);
  }

  private cargar() {
    const hoy = new Date();
    const hoyIso = isoLocal(hoy);
    forkJoin({
      productos: this.productoService.listarTodos().pipe(catchError(() => of([] as Producto[]))),
      postventa: this.mantenimientoService.resumen().pipe(catchError(() => of(null as ResumenPostventa | null))),
      cuentas: this.cobranzaService.porCobrar().pipe(catchError(() => of([] as CuentaPorCobrar[]))),
      devoluciones: this.cobranzaService.devoluciones().pipe(catchError(() => of([] as Devolucion[]))),
      proformas: this.proformaService.listar().pipe(catchError(() => of([] as ProformaResponse[]))),
      ordenes: this.ordenService.listar({ estado: 'PROGRAMADA', hasta: hoyIso }).pipe(catchError(() => of([] as OrdenTrabajo[]))),
    }).subscribe((r) => {
      const lista: Alerta[] = [];
      const plural = (n: number, uno: string, varios: string) => `${n} ${n === 1 ? uno : varios}`;

      const vencidas = r.cuentas.filter((c) => c.diasAtraso > 0);
      if (vencidas.length) {
        const peor = Math.max(...vencidas.map((c) => c.diasAtraso));
        lista.push({ nivel: 'alta', titulo: plural(vencidas.length, 'cuenta vencida', 'cuentas vencidas'),
          detalle: `La más atrasada tiene ${peor} día(s) de atraso`, ruta: '/cobranza', query: { vencidas: '1' } });
      }
      if (r.postventa && r.postventa.vencidos > 0) {
        lista.push({ nivel: 'alta', titulo: plural(r.postventa.vencidos, 'mantenimiento vencido', 'mantenimientos vencidos'),
          detalle: 'Visitas cuya fecha ya pasó sin atenderse', ruta: '/postventa' });
      }
      const atrasadas = r.ordenes.filter((o) => o.fechaProgramada < hoyIso);
      const deHoy = r.ordenes.filter((o) => o.fechaProgramada === hoyIso);
      if (atrasadas.length) {
        lista.push({ nivel: 'alta', titulo: plural(atrasadas.length, 'orden sin cerrar', 'órdenes sin cerrar'),
          detalle: 'Su fecha ya pasó y siguen programadas', ruta: '/servicios/ordenes' });
      }
      const stockBajo = r.productos.filter((p) => p.sector === 'MATERIAL_SERVICIO' && (p.stockActual ?? 0) <= (p.stockMinimo ?? 0));
      if (stockBajo.length) {
        lista.push({ nivel: 'media', titulo: plural(stockBajo.length, 'material con stock bajo', 'materiales con stock bajo'),
          detalle: stockBajo.slice(0, 2).map((p) => p.nombre).join(', ') + (stockBajo.length > 2 ? '…' : ''), ruta: '/catalogo/productos' });
      }
      const porVencer = r.proformas.filter((p) => {
        if (p.estado !== 'PENDIENTE' || !p.diasVigencia) return false;
        const vence = new Date(p.fechaEmision + 'T00:00');
        vence.setDate(vence.getDate() + p.diasVigencia);
        const dias = Math.round((vence.getTime() - new Date(hoyIso + 'T00:00').getTime()) / 86400000);
        return dias >= 0 && dias <= 3;
      });
      if (porVencer.length) {
        lista.push({ nivel: 'media', titulo: plural(porVencer.length, 'proforma vence pronto', 'proformas vencen pronto'),
          detalle: 'En 3 días o menos: conviene llamar al cliente', ruta: '/proformas' });
      }
      const devoluciones = r.devoluciones.filter((d) => d.estado === 'PENDIENTE');
      if (devoluciones.length && this.auth.tieneRol('ADMIN')) {
        lista.push({ nivel: 'media', titulo: plural(devoluciones.length, 'devolución pendiente', 'devoluciones pendientes'),
          detalle: 'Dinero por devolver de ventas anuladas', ruta: '/cobranza', query: { vista: 'DEVOLUCIONES' } });
      }
      if (deHoy.length) {
        lista.push({ nivel: 'info', titulo: plural(deHoy.length, 'orden de trabajo hoy', 'órdenes de trabajo hoy'),
          detalle: [...new Set(deHoy.map((o) => o.tecnico))].join(', '), ruta: '/servicios/ordenes' });
      }
      if (r.postventa && r.postventa.proximos7 > 0) {
        lista.push({ nivel: 'info', titulo: plural(r.postventa.proximos7, 'mantenimiento esta semana', 'mantenimientos esta semana'),
          detalle: 'Coordinar la visita con el cliente', ruta: '/postventa' });
      }
      this.alertas.set(lista);
    });
  }
}
