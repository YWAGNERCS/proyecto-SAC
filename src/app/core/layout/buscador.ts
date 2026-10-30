import { Component, ElementRef, HostListener, computed, inject, signal, viewChild } from '@angular/core';
import { Router } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { AuthService } from '../auth/auth.service';
import { ClienteService } from '../../features/clientes/cliente/cliente-service';
import { Cliente } from '../../features/clientes/cliente/cliente.model';
import { ProveedorService } from '../../features/proveedores/proveedor/proveedor-service';
import { Proveedor } from '../../features/proveedores/proveedor/proveedor.model';
import { ProductoService } from '../../features/catalogo/producto/producto-service';
import { Producto } from '../../features/catalogo/producto/producto.model';
import { GRUPOS, ICONOS } from './menu';

interface Resultado {
  tipo: 'Pantalla' | 'Cliente' | 'Proveedor' | 'Producto' | 'Orden de trabajo';
  texto: string;
  detalle: string;
  ruta: string[];
  icono: string;
}

/** Quita tildes y pasa a minúsculas para comparar ("Breña" encuentra "brena"). */
function normalizar(t: string | null | undefined): string {
  return (t ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
}

/**
 * Buscador global de la barra superior (Ctrl + K): pantallas, clientes (nombre, RUC o DNI),
 * proveedores, productos y órdenes de trabajo por número ("#12" o "12").
 * Los catálogos se piden una sola vez, al primer uso; con ↑ ↓ se elige y con Enter se abre.
 */
@Component({
  selector: 'app-buscador',
  templateUrl: './buscador.html',
  styleUrl: './buscador.css',
})
export class BuscadorComponent {
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly clienteService = inject(ClienteService);
  private readonly proveedorService = inject(ProveedorService);
  private readonly productoService = inject(ProductoService);
  private readonly campo = viewChild<ElementRef<HTMLInputElement>>('campo');
  private readonly host = inject(ElementRef<HTMLElement>);

  readonly iconoBuscar = ICONOS.buscar;
  readonly texto = signal('');
  readonly abierto = signal(false);
  readonly cargando = signal(false);
  readonly seleccion = signal(0);

  private cargado = false;
  private readonly clientes = signal<Cliente[]>([]);
  private readonly proveedores = signal<Proveedor[]>([]);
  private readonly productos = signal<Producto[]>([]);

  readonly resultados = computed<Resultado[]>(() => {
    const q = normalizar(this.texto().trim());
    if (q.length < 2 && !/^#?\d+$/.test(q)) return [];
    const salida: Resultado[] = [];

    // Pantallas del menú (respeta las solo para ADMIN)
    for (const g of GRUPOS) {
      for (const o of g.opciones) {
        if (o.soloAdmin && !this.auth.tieneRol('ADMIN')) continue;
        if (normalizar(o.texto).includes(q) && !salida.some((r) => r.ruta[0] === o.ruta)) {
          salida.push({ tipo: 'Pantalla', texto: o.texto, detalle: g.titulo, ruta: [o.ruta], icono: o.icono });
        }
      }
    }
    // Número: orden de trabajo
    const numero = q.replace('#', '');
    if (/^\d+$/.test(numero)) {
      salida.push({ tipo: 'Orden de trabajo', texto: `Orden de trabajo #${numero}`, detalle: 'Abrir la orden',
        ruta: ['/servicios/ordenes', numero], icono: ICONOS.ordenes });
    }
    for (const c of this.clientes()) {
      if (normalizar(c.nombreORazonSocial).includes(q) || (c.ruc ?? '').includes(numero) && numero.length >= 4
          || (c.dni ?? '').includes(numero) && numero.length >= 4) {
        salida.push({ tipo: 'Cliente', texto: c.nombreORazonSocial, detalle: c.ruc ? 'RUC ' + c.ruc : c.dni ? 'DNI ' + c.dni : 'Cliente',
          ruta: ['/clientes', String(c.id), 'historial'], icono: ICONOS.clientes });
      }
    }
    for (const p of this.proveedores()) {
      if (normalizar(p.nombreORazonSocial).includes(q) || (p.ruc ?? '').includes(numero) && numero.length >= 4) {
        salida.push({ tipo: 'Proveedor', texto: p.nombreORazonSocial, detalle: p.ruc ? 'RUC ' + p.ruc : 'Proveedor',
          ruta: ['/proveedores', String(p.id), 'historial'], icono: ICONOS.proveedores });
      }
    }
    for (const p of this.productos()) {
      if (normalizar(p.nombre).includes(q)) {
        salida.push({ tipo: 'Producto', texto: p.nombre,
          detalle: p.sector === 'MATERIAL_SERVICIO' ? `Stock ${p.stockActual ?? 0}` : (p.categoriaNombre ?? 'Producto'),
          ruta: ['/catalogo/productos', String(p.id), 'kardex'], icono: ICONOS.productos });
      }
    }
    return salida.slice(0, 9);
  });

  /** Ctrl + K (o Cmd + K) enfoca el buscador desde cualquier pantalla. */
  @HostListener('document:keydown', ['$event'])
  atajo(e: KeyboardEvent) {
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
      e.preventDefault();
      this.campo()?.nativeElement.focus();
    }
  }

  @HostListener('document:click', ['$event'])
  clicAfuera(e: MouseEvent) {
    if (!this.host.nativeElement.contains(e.target as Node)) this.abierto.set(false);
  }

  enfocar() {
    this.abierto.set(true);
    if (this.cargado) return;
    this.cargado = true;
    this.cargando.set(true);
    forkJoin({
      clientes: this.clienteService.listarTodos().pipe(catchError(() => of([] as Cliente[]))),
      proveedores: this.proveedorService.listarTodos().pipe(catchError(() => of([] as Proveedor[]))),
      productos: this.productoService.listarTodos().pipe(catchError(() => of([] as Producto[]))),
    }).subscribe((r) => {
      this.clientes.set(r.clientes);
      this.proveedores.set(r.proveedores);
      this.productos.set(r.productos);
      this.cargando.set(false);
    });
  }

  escribir(valor: string) {
    this.texto.set(valor);
    this.seleccion.set(0);
    this.abierto.set(true);
  }

  tecla(e: KeyboardEvent) {
    const total = this.resultados().length;
    if (e.key === 'ArrowDown' && total) {
      e.preventDefault();
      this.seleccion.update((i) => (i + 1) % total);
    } else if (e.key === 'ArrowUp' && total) {
      e.preventDefault();
      this.seleccion.update((i) => (i - 1 + total) % total);
    } else if (e.key === 'Enter' && total) {
      e.preventDefault();
      this.ir(this.resultados()[this.seleccion()]);
    } else if (e.key === 'Escape') {
      this.abierto.set(false);
      (e.target as HTMLInputElement).blur();
    }
  }

  ir(r: Resultado) {
    this.router.navigate(r.ruta);
    this.texto.set('');
    this.abierto.set(false);
    this.campo()?.nativeElement.blur();
  }
}
