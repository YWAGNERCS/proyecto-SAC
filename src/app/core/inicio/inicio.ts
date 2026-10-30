import { Component, ElementRef, HostListener, computed, effect, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Observable, catchError, forkJoin, of } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { PreferenciasService } from '../services/preferencias-service';
import { ICONOS, iniciales, tonoAvatar } from '../layout/menu';
import { VentaService } from '../../features/ventas/venta/venta-service';
import { VentaResponse } from '../../features/ventas/venta/venta.model';
import { CobranzaService } from '../../features/cobranza/cobro/cobranza-service';
import { Cobro, CuentaPorCobrar, METODOS_PAGO } from '../../features/cobranza/cobro/cobranza.model';
import { OrdenService } from '../../features/servicios/orden/orden-service';
import { OrdenTrabajo, etiquetaTipo } from '../../features/servicios/orden/orden.model';

type Preset = 'MES' | 'MES_ANTERIOR' | 'TREINTA' | 'TRIMESTRE' | 'ANIO' | 'PERSONALIZADO';
type ModoBarras = 'MENSUAL' | 'ANUAL';

interface Barra {
  etiqueta: string;
  total: number;
  cantidad: number;
}

/** Fecha local en formato ISO (yyyy-mm-dd), sin el desfase de UTC. */
function iso(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function dia(isoFecha: string): Date {
  return new Date(isoFecha + 'T00:00');
}

const MESES = ['Ene', 'Feb', 'Mar', 'Abr', 'May', 'Jun', 'Jul', 'Ago', 'Set', 'Oct', 'Nov', 'Dic'];

/**
 * Inicio. Sin sesión es pública y ofrece iniciar sesión en Keycloak.
 * Con sesión es el tablero (referencia "Quixotic") con datos reales de JD, filtrado por un rango de fechas:
 * ventas del periodo y su variación, cobrado en la semana, ventas por mes o por año, lo que queda por
 * cobrar con la curva de cobros, crédito vencido, últimos cobros y órdenes de trabajo de hoy.
 * Solo lee, con los mismos endpoints de cada pantalla. Los montos en dólares se pasan a soles con el
 * tipo de cambio de su venta para poder sumarlos.
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
  private readonly ordenService = inject(OrdenService);
  private readonly host = inject(ElementRef<HTMLElement>);

  readonly iconos = ICONOS;
  readonly iniciales = iniciales;
  readonly tonoAvatar = tonoAvatar;
  readonly etiquetaTipo = etiquetaTipo;

  readonly cargando = signal(true);
  readonly error = signal(false);
  private readonly ventas = signal<VentaResponse[]>([]);
  private readonly cobros = signal<Cobro[]>([]);
  private readonly cuentas = signal<CuentaPorCobrar[]>([]);
  private readonly ordenes = signal<OrdenTrabajo[]>([]);

  private readonly hoyIso = iso(new Date());

  // ── Rango de fechas ──
  /** El tablero abre con el periodo elegido en Configuración (por defecto, este mes). */
  private readonly periodoInicial = inject(PreferenciasService).periodoTablero();
  readonly preset = signal<Preset>(this.periodoInicial);
  readonly desde = signal(this.rangoDe(this.periodoInicial).desde);
  readonly hasta = signal(this.rangoDe(this.periodoInicial).hasta);
  readonly selectorAbierto = signal(false);
  readonly presets: { valor: Preset; etiqueta: string }[] = [
    { valor: 'MES', etiqueta: 'Este mes' },
    { valor: 'MES_ANTERIOR', etiqueta: 'Mes anterior' },
    { valor: 'TREINTA', etiqueta: 'Últimos 30 días' },
    { valor: 'TRIMESTRE', etiqueta: 'Este trimestre' },
    { valor: 'ANIO', etiqueta: 'Este año' },
  ];

  readonly textoRango = computed(() => {
    const f = (s: string) => dia(s).toLocaleDateString('es-PE', { day: 'numeric', month: 'short', year: 'numeric' }).replace('.', '');
    return `${f(this.desde())} – ${f(this.hasta())}`;
  });

  /** Tipo de cambio de cada venta (para pasar a soles lo cobrado o adeudado en dólares). */
  private readonly tcVenta = computed(() => new Map(this.ventas().map((v) => [v.id, Number(v.tipoCambio) || 0])));
  /** Tipo de cambio de referencia: el de la venta más reciente que lo tenga. */
  private readonly tcReferencia = computed(() =>
    Number([...this.ventas()].sort((a, b) => b.id - a.id).find((v) => v.tipoCambio)?.tipoCambio) || 3.75);

  private aSoles(monto: number, moneda: string, tc?: number | null): number {
    return moneda === 'DOLARES' ? Number(monto) * (tc || this.tcReferencia()) : Number(monto);
  }

  private totalVenta(v: VentaResponse): number {
    return this.aSoles(Number(v.total), v.moneda, Number(v.tipoCambio));
  }

  private vigentesEntre(desde: string, hasta: string) {
    return this.ventas().filter((v) => v.estado === 'REGISTRADA' && v.fechaVenta >= desde && v.fechaVenta <= hasta);
  }

  // ── Ventas del periodo (tarjeta verde) ──
  readonly ventasPeriodo = computed(() => {
    const lista = this.vigentesEntre(this.desde(), this.hasta());
    // Periodo anterior de la misma duración, justo antes
    const largo = Math.round((dia(this.hasta()).getTime() - dia(this.desde()).getTime()) / 86400000) + 1;
    const finAnterior = dia(this.desde());
    finAnterior.setDate(finAnterior.getDate() - 1);
    const inicioAnterior = new Date(finAnterior);
    inicioAnterior.setDate(inicioAnterior.getDate() - (largo - 1));
    const total = lista.reduce((s, v) => s + this.totalVenta(v), 0);
    const anterior = this.vigentesEntre(iso(inicioAnterior), iso(finAnterior)).reduce((s, v) => s + this.totalVenta(v), 0);
    return {
      total,
      cantidad: lista.length,
      dolares: lista.filter((v) => v.moneda === 'DOLARES').reduce((s, v) => s + Number(v.total), 0),
      variacion: anterior > 0 ? ((total - anterior) / anterior) * 100 : null,
      ticket: lista.length ? total / lista.length : 0,
    };
  });

  // ── Cobrado en los últimos 7 días ──
  private cobradoEntre(desde: string, hasta: string): number {
    return this.cobros()
      .filter((c) => c.estado !== 'ANULADO' && c.metodoPago !== 'DETRACCION' && c.metodoPago !== 'RETENCION'
        && c.fecha >= desde && c.fecha <= hasta)
      .reduce((s, c) => s + this.aSoles(Number(c.montoTotal), c.moneda, this.tcVenta().get(c.abonos[0]?.ventaId)), 0);
  }

  readonly cobradoSemana = computed(() => {
    const d = (n: number) => { const x = new Date(); x.setDate(x.getDate() - n); return iso(x); };
    const actual = this.cobradoEntre(d(6), this.hoyIso);
    const anterior = this.cobradoEntre(d(13), d(7));
    return { total: actual, variacion: anterior > 0 ? ((actual - anterior) / anterior) * 100 : null };
  });

  // ── Barras: ventas por mes o por año ──
  readonly modoBarras = signal<ModoBarras>('MENSUAL');

  readonly barras = computed<Barra[]>(() => {
    const fin = dia(this.hasta());
    const salida: Barra[] = [];
    if (this.modoBarras() === 'MENSUAL') {
      for (let i = 5; i >= 0; i--) {
        const m = new Date(fin.getFullYear(), fin.getMonth() - i, 1);
        const desde = iso(m);
        const hasta = iso(new Date(m.getFullYear(), m.getMonth() + 1, 0));
        const lista = this.vigentesEntre(desde, hasta);
        salida.push({ etiqueta: MESES[m.getMonth()], total: lista.reduce((s, v) => s + this.totalVenta(v), 0), cantidad: lista.length });
      }
    } else {
      for (let i = 4; i >= 0; i--) {
        const anio = fin.getFullYear() - i;
        const lista = this.vigentesEntre(`${anio}-01-01`, `${anio}-12-31`);
        salida.push({ etiqueta: String(anio), total: lista.reduce((s, v) => s + this.totalVenta(v), 0), cantidad: lista.length });
      }
    }
    return salida;
  });

  /** Lectura del gráfico para lectores de pantalla. */
  readonly descripcionBarras = computed(() => `Ventas por ${this.modoBarras() === 'MENSUAL' ? 'mes' : 'año'}: `
    + this.barras().map((b) => `${b.etiqueta} S/ ${Math.round(b.total).toLocaleString('en-US')}`).join(', '));

  readonly maxBarra = computed(() => Math.max(1, ...this.barras().map((b) => b.total)));
  /** Escala del eje: 4 marcas redondeadas hacia arriba. */
  readonly ejeBarras = computed(() => {
    const paso = this.redondearArriba(this.maxBarra() / 4);
    return [4, 3, 2, 1, 0].map((i) => i * paso);
  });
  readonly topeEje = computed(() => this.ejeBarras()[0] || 1);
  readonly variacionUltimaBarra = computed(() => {
    const b = this.barras();
    const ultima = b[b.length - 1]?.total ?? 0;
    const penultima = b[b.length - 2]?.total ?? 0;
    return penultima > 0 ? ((ultima - penultima) / penultima) * 100 : null;
  });

  // ── Por cobrar con la curva de cobros por semana ──
  readonly porCobrar = computed(() => {
    const cuentas = this.cuentas();
    const saldo = (c: CuentaPorCobrar) => this.aSoles(Number(c.saldo), c.moneda, this.tcVenta().get(c.ventaId));
    const vencidas = cuentas.filter((c) => c.diasAtraso > 0);
    return {
      total: cuentas.reduce((s, c) => s + saldo(c), 0),
      cuentas: cuentas.length,
      vencido: vencidas.reduce((s, c) => s + saldo(c), 0),
      vencidas: vencidas.length,
      masAtrasada: vencidas.length ? Math.max(...vencidas.map((c) => c.diasAtraso)) : 0,
      deduccion: cuentas.reduce((s, c) => s + this.aSoles(Number(c.deduccionPendiente ?? 0), c.moneda, this.tcVenta().get(c.ventaId)), 0),
    };
  });

  /** Cobrado por semana en las últimas 10 semanas (la curva de la tarjeta "Por cobrar"). */
  readonly semanas = computed(() => {
    const puntos: number[] = [];
    for (let i = 9; i >= 0; i--) {
      const fin = new Date(); fin.setDate(fin.getDate() - i * 7);
      const ini = new Date(fin); ini.setDate(ini.getDate() - 6);
      puntos.push(this.cobradoEntre(iso(ini), iso(fin)));
    }
    return puntos;
  });

  /** Trazo suave de la curva (y su relleno) en un lienzo de 300 × 110. */
  readonly curva = computed(() => {
    const p = this.semanas();
    const max = Math.max(1, ...p);
    const xy = p.map((v, i) => [(i / (p.length - 1)) * 300, 100 - (v / max) * 80] as const);
    let d = `M ${xy[0][0]} ${xy[0][1]}`;
    for (let i = 1; i < xy.length; i++) {
      const [x0, y0] = xy[i - 1];
      const [x1, y1] = xy[i];
      const cx = (x0 + x1) / 2;
      d += ` C ${cx} ${y0}, ${cx} ${y1}, ${x1} ${y1}`;
    }
    return { linea: d, area: `${d} L 300 110 L 0 110 Z`, ultimo: xy[xy.length - 1] };
  });

  // ── Últimos cobros y órdenes de hoy ──
  readonly ultimosCobros = computed(() => [...this.cobros()].sort((a, b) => b.id - a.id).slice(0, 5));

  readonly ordenesHoy = computed(() => this.ordenes().filter((o) => o.fechaProgramada === this.hoyIso));
  readonly tecnicosHoy = computed(() => [...new Set(this.ordenesHoy().map((o) => o.tecnico))]);

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
    const seguro = <T>(obs: Observable<T>, vacio: T) => obs.pipe(catchError(() => { fallas++; return of(vacio); }));
    forkJoin({
      ventas: seguro(this.ventaService.listar(), [] as VentaResponse[]),
      cobros: seguro(this.cobranzaService.historial(), [] as Cobro[]),
      cuentas: seguro(this.cobranzaService.porCobrar(), [] as CuentaPorCobrar[]),
      ordenes: seguro(this.ordenService.listar({ estado: 'PROGRAMADA', desde: this.hoyIso, hasta: this.hoyIso }), [] as OrdenTrabajo[]),
    }).subscribe((r) => {
      this.ventas.set(r.ventas);
      this.cobros.set(r.cobros);
      this.cuentas.set(r.cuentas);
      this.ordenes.set(r.ordenes);
      this.error.set(fallas > 0);
      this.cargando.set(false);
    });
  }

  // ── Selector de rango ──
  elegirPreset(p: Preset) {
    const r = this.rangoDe(p);
    this.preset.set(p);
    this.desde.set(r.desde);
    this.hasta.set(r.hasta);
    this.selectorAbierto.set(false);
  }

  fijarFecha(campo: 'desde' | 'hasta', valor: string) {
    if (!valor) return;
    this.preset.set('PERSONALIZADO');
    (campo === 'desde' ? this.desde : this.hasta).set(valor);
    if (this.desde() > this.hasta()) (campo === 'desde' ? this.hasta : this.desde).set(valor);
  }

  @HostListener('document:click', ['$event'])
  clicAfuera(e: MouseEvent) {
    const selector = this.host.nativeElement.querySelector('.rango');
    if (selector && !selector.contains(e.target as Node)) this.selectorAbierto.set(false);
  }

  private rangoDe(p: Preset): { desde: string; hasta: string } {
    const h = new Date();
    switch (p) {
      case 'MES_ANTERIOR':
        return { desde: iso(new Date(h.getFullYear(), h.getMonth() - 1, 1)), hasta: iso(new Date(h.getFullYear(), h.getMonth(), 0)) };
      case 'TREINTA': {
        const d = new Date(h); d.setDate(d.getDate() - 29);
        return { desde: iso(d), hasta: iso(h) };
      }
      case 'TRIMESTRE':
        return { desde: iso(new Date(h.getFullYear(), Math.floor(h.getMonth() / 3) * 3, 1)), hasta: iso(h) };
      case 'ANIO':
        return { desde: `${h.getFullYear()}-01-01`, hasta: iso(h) };
      default:
        return { desde: iso(new Date(h.getFullYear(), h.getMonth(), 1)), hasta: iso(h) };
    }
  }

  // ── Ayudas de presentación ──
  saludo(): string {
    const h = new Date().getHours();
    return h < 12 ? 'Buenos días' : h < 19 ? 'Buenas tardes' : 'Buenas noches';
  }

  primerNombre(): string {
    const u = this.auth.usuario();
    return (u?.nombre || u?.username || '').split(' ')[0];
  }

  /** "32,678.90" → entero "32,678" y decimales ".90" (los decimales se muestran más claros). */
  partes(n: number): { entero: string; decimales: string } {
    const [entero, dec] = Number(n || 0).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).split('.');
    return { entero, decimales: '.' + dec };
  }

  /** "12.8 %" con signo; null si no hay con qué comparar. */
  variacion(v: number | null): string | null {
    return v === null ? null : `${v >= 0 ? '+' : ''}${v.toFixed(1)} %`;
  }

  compacto(n: number): string {
    return n >= 1000 ? `${(n / 1000).toFixed(n >= 10000 ? 0 : 1)}k` : String(Math.round(n));
  }

  etiquetaMetodo(m: string): string {
    return METODOS_PAGO.find((x) => x.valor === m)?.etiqueta ?? m;
  }

  simbolo(moneda: string): string {
    return moneda === 'DOLARES' ? 'US$' : 'S/';
  }

  alturaBarra(total: number): number {
    return total > 0 ? Math.max(4, (total / this.topeEje()) * 100) : 0;
  }

  private redondearArriba(n: number): number {
    if (n <= 0) return 1;
    const potencia = 10 ** Math.floor(Math.log10(n));
    return Math.ceil(n / potencia) * potencia;
  }
}
