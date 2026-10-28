import { Component, computed, effect, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Observable, catchError, forkJoin, of } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { VentaService } from '../../features/ventas/venta/venta-service';
import { VentaResponse } from '../../features/ventas/venta/venta.model';
import { CobranzaService } from '../../features/cobranza/cobro/cobranza-service';
import { CuentaPorCobrar } from '../../features/cobranza/cobro/cobranza.model';
import { MantenimientoService } from '../../features/postventa/mantenimiento/mantenimiento-service';
import { ResumenPostventa } from '../../features/postventa/mantenimiento/mantenimiento.model';
import { ProductoService } from '../../features/catalogo/producto/producto-service';
import { Producto } from '../../features/catalogo/producto/producto.model';
import { ProformaService } from '../../features/proformas/proforma/proforma-service';
import { ProformaResponse } from '../../features/proformas/proforma/proforma.model';

/** Un día del gráfico de ventas de la semana. */
interface DiaVentas {
  fecha: string;
  etiqueta: string;
  total: number;
  cantidad: number;
}

/** Fecha local en formato ISO (yyyy-mm-dd), sin el desfase de UTC. */
function isoLocal(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/**
 * Inicio. Sin sesión es pública y ofrece iniciar sesión en Keycloak.
 * Con sesión es el tablero del día: indicadores de ventas, cobranza, postventa,
 * stock y proformas, más las últimas ventas. Solo lee: usa los mismos endpoints
 * que las pantallas de cada módulo, así que ve lo mismo que el rol del usuario.
 */
@Component({
  selector: 'app-inicio',
  imports: [RouterLink, DecimalPipe],
  templateUrl: './inicio.html',
  styleUrl: './inicio.css',
})
export class Inicio {
  readonly auth = inject(AuthService);
  private readonly ventaService = inject(VentaService);
  private readonly cobranzaService = inject(CobranzaService);
  private readonly mantenimientoService = inject(MantenimientoService);
  private readonly productoService = inject(ProductoService);
  private readonly proformaService = inject(ProformaService);

  readonly cargando = signal(true);
  readonly error = signal(false);

  private readonly ventas = signal<VentaResponse[]>([]);
  private readonly porCobrar = signal<CuentaPorCobrar[]>([]);
  readonly postventa = signal<ResumenPostventa | null>(null);
  private readonly productos = signal<Producto[]>([]);
  private readonly proformas = signal<ProformaResponse[]>([]);

  private readonly hoy = new Date();
  private readonly inicioMes = isoLocal(new Date(this.hoy.getFullYear(), this.hoy.getMonth(), 1));
  readonly mesActual = this.hoy.toLocaleDateString('es-PE', { month: 'long', year: 'numeric' });

  /** Ventas vigentes (sin anuladas) del mes en curso, por moneda. */
  readonly ventasMes = computed(() => {
    const delMes = this.ventas().filter((v) => v.estado === 'REGISTRADA' && v.fechaVenta >= this.inicioMes);
    const suma = (m: string) => delMes.filter((v) => v.moneda === m).reduce((t, v) => t + Number(v.total), 0);
    return { cantidad: delMes.length, soles: suma('SOLES'), dolares: suma('DOLARES') };
  });

  /** Saldos por cobrar por moneda y cuántas cuentas ya vencieron. */
  readonly cobranza = computed(() => {
    const cuentas = this.porCobrar();
    const suma = (m: string) => cuentas.filter((c) => c.moneda === m).reduce((t, c) => t + Number(c.saldo), 0);
    return { cuentas: cuentas.length, soles: suma('SOLES'), dolares: suma('DOLARES'), vencidas: cuentas.filter((c) => c.diasAtraso > 0).length };
  });

  /** Materiales en o bajo su stock mínimo (los equipos no llevan stock). */
  readonly stockBajo = computed(() =>
    this.productos()
      .filter((p) => p.sector === 'MATERIAL_SERVICIO' && (p.stockActual ?? 0) <= (p.stockMinimo ?? 0))
      .sort((a, b) => (a.stockActual ?? 0) - (b.stockActual ?? 0)),
  );

  readonly proformasPendientes = computed(() => this.proformas().filter((p) => p.estado === 'PENDIENTE').length);

  /** Ventas en soles de los últimos 7 días, para el gráfico de barras. */
  readonly semana = computed<DiaVentas[]>(() => {
    const dias: DiaVentas[] = [];
    for (let i = 6; i >= 0; i--) {
      const d = new Date(this.hoy.getFullYear(), this.hoy.getMonth(), this.hoy.getDate() - i);
      const fecha = isoLocal(d);
      const delDia = this.ventas().filter((v) => v.estado === 'REGISTRADA' && v.moneda === 'SOLES' && v.fechaVenta === fecha);
      dias.push({
        fecha,
        etiqueta: d.toLocaleDateString('es-PE', { weekday: 'short' }).replace('.', ''),
        total: delDia.reduce((t, v) => t + Number(v.total), 0),
        cantidad: delDia.length,
      });
    }
    return dias;
  });

  readonly maximoSemana = computed(() => Math.max(1, ...this.semana().map((d) => d.total)));
  readonly totalSemana = computed(() => this.semana().reduce((t, d) => t + d.total, 0));

  /** Las 6 ventas más recientes. */
  readonly ultimasVentas = computed(() => [...this.ventas()].sort((a, b) => b.id - a.id).slice(0, 6));

  constructor() {
    // El tablero se carga al tener sesión (también si la sesión llega después, al volver de Keycloak)
    effect(() => {
      if (this.auth.autenticado()) this.cargar();
    });
  }

  cargar(): void {
    this.cargando.set(true);
    this.error.set(false);
    let fallas = 0;
    const seguro = <T>(obs: Observable<T>, vacio: T) =>
      obs.pipe(catchError(() => { fallas++; return of(vacio); }));

    forkJoin({
      ventas: seguro(this.ventaService.listar(), [] as VentaResponse[]),
      porCobrar: seguro(this.cobranzaService.porCobrar(), [] as CuentaPorCobrar[]),
      postventa: seguro(this.mantenimientoService.resumen(), null as ResumenPostventa | null),
      productos: seguro(this.productoService.listarTodos(), [] as Producto[]),
      proformas: seguro(this.proformaService.listar(), [] as ProformaResponse[]),
    }).subscribe((r) => {
      this.ventas.set(r.ventas);
      this.porCobrar.set(r.porCobrar);
      this.postventa.set(r.postventa);
      this.productos.set(r.productos);
      this.proformas.set(r.proformas);
      this.error.set(fallas > 0);
      this.cargando.set(false);
    });
  }

  saludo(): string {
    const h = new Date().getHours();
    return h < 12 ? 'Buenos días' : h < 19 ? 'Buenas tardes' : 'Buenas noches';
  }

  primerNombre(): string {
    const u = this.auth.usuario();
    return (u?.nombre || u?.username || '').split(' ')[0];
  }

  simbolo(moneda: string): string {
    return moneda === 'DOLARES' ? 'US$' : 'S/';
  }

  /** Altura de cada barra (en % del día con más ventas); mínimo visible si hubo ventas. */
  alturaBarra(total: number): number {
    return total > 0 ? Math.max(6, (total / this.maximoSemana()) * 100) : 0;
  }
}
