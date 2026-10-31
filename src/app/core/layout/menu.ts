import { Permiso } from '../auth/auth.service';

/**
 * Menú de SGE-JD: grupos (píldoras de la barra superior) y sus módulos (íconos del riel izquierdo).
 * Lo usan la barra, el riel, el menú del celular y el buscador global ("ir a…").
 * `icono` es el trazo SVG de 24×24 en estilo línea.
 */
export interface OpcionMenu {
  ruta: string;
  texto: string;
  icono: string;
  /** Solo ADMIN o quien tenga este permiso delegado: misma regla que SecurityConfig y permisoGuard. */
  permiso?: Permiso;
  /** Rutas extra que también pertenecen a este módulo (para saber qué está activo). */
  prefijos?: string[];
}

export interface GrupoMenu {
  id: string;
  titulo: string;
  icono: string;
  opciones: OpcionMenu[];
}

export const ICONOS = {
  inicio: 'M4 4h6v6H4z M14 4h6v6h-6z M4 14h6v6H4z M14 14h6v6h-6z',
  proformas: 'M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z M14 3v5h5 M9 13h6 M9 17h6',
  ventas: 'M3 4h2l2.4 11.2a2 2 0 0 0 2 1.6h7.7a2 2 0 0 0 2-1.5L21 8H6.2 M9 21a1 1 0 1 0 0-2 1 1 0 0 0 0 2 M18 21a1 1 0 1 0 0-2 1 1 0 0 0 0 2',
  cobranza: 'M19 7V5a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-3 M3 7h18v4 M21 11h-5a2 2 0 0 0 0 4h5z',
  postventa: 'M14.7 6.3a4 4 0 0 0-5.4 5.4L3 18l3 3 6.3-6.3a4 4 0 0 0 5.4-5.4l-2.6 2.6-2.4-.6-.6-2.4z',
  compras: 'M21 8 12 3 3 8v8l9 5 9-5z M3 8l9 5 9-5 M12 13v8',
  productos: 'M4 8h16v12H4z M2 4h20v4H2z M10 12h4',
  categorias: 'M20.6 13.4l-7.2 7.2a2 2 0 0 1-2.8 0L3 13V3h10l7.6 7.6a2 2 0 0 1 0 2.8z M7.5 7.5h.01',
  clientes: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8 M22 21v-2a4 4 0 0 0-3-3.9 M16 3.1a4 4 0 0 1 0 7.8',
  proveedores: 'M4 21V5a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v16 M16 9h3a2 2 0 0 1 2 2v10 M2 21h20 M8 7h4 M8 11h4 M8 15h4',
  nuevo: 'M12 5v14 M5 12h14',
  ordenes: 'M9 5H7a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-2 M9 5a2 2 0 0 0 2 2h2a2 2 0 0 0 2-2 2 2 0 0 0-2-2h-2a2 2 0 0 0-2 2z M9 12l2 2 4-4',
  equipos: 'M3 6h18v8H3z M7 18v-4 M17 18v-4 M7 10h.01 M11 10h6',
  contratos: 'M8 2v4 M16 2v4 M3 10h18 M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z M8 14h.01 M12 14h.01 M16 14h.01',
  comercial: 'M3 3v18h18 M7 15l4-4 3 3 5-6',
  servicios: 'M14.7 6.3a4 4 0 0 0-5.4 5.4L3 18l3 3 6.3-6.3a4 4 0 0 0 5.4-5.4l-2.6 2.6-2.4-.6-.6-2.4z',
  inventario: 'M21 8 12 3 3 8v8l9 5 9-5z M3 8l9 5 9-5 M12 13v8',
  contactos: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8',
  salir: 'M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4 M16 17l5-5-5-5 M21 12H9',
  engranaje: 'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z',
  buscar: 'M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16z M21 21l-4.3-4.3',
  campana: 'M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9 M13.7 21a2 2 0 0 1-3.4 0',
  menu: 'M4 6h16 M4 12h16 M4 18h16',
  flecha: 'M7 17L17 7 M7 7h10v10',
  sol: 'M12 17a5 5 0 1 0 0-10 5 5 0 0 0 0 10z M12 1v2 M12 21v2 M4.2 4.2l1.4 1.4 M18.4 18.4l1.4 1.4 M1 12h2 M21 12h2 M4.2 19.8l1.4-1.4 M18.4 5.6l1.4-1.4',
  luna: 'M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z',
  escudo: 'M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z M9 12l2 2 4-4',
  historial: 'M3 12a9 9 0 1 0 3-6.7L3 8 M3 3v5h5 M12 7v5l4 2',
};

export const GRUPOS: GrupoMenu[] = [
  {
    id: 'inicio', titulo: 'Inicio', icono: ICONOS.inicio,
    // En Inicio, el riel ofrece los accesos al trabajo diario
    opciones: [
      { ruta: '/', texto: 'Tablero', icono: ICONOS.inicio },
      { ruta: '/ventas/nueva', texto: 'Nueva venta', icono: ICONOS.ventas },
      { ruta: '/proformas/nueva', texto: 'Nueva proforma', icono: ICONOS.proformas },
      { ruta: '/servicios/ordenes/nueva', texto: 'Nueva orden de trabajo', icono: ICONOS.ordenes },
      { ruta: '/cobranza', texto: 'Registrar un cobro', icono: ICONOS.cobranza },
    ],
  },
  {
    id: 'comercial', titulo: 'Comercial', icono: ICONOS.comercial,
    opciones: [
      { ruta: '/proformas', texto: 'Proformas', icono: ICONOS.proformas },
      { ruta: '/ventas', texto: 'Ventas', icono: ICONOS.ventas },
      { ruta: '/cobranza', texto: 'Cobranza', icono: ICONOS.cobranza },
    ],
  },
  {
    id: 'servicios', titulo: 'Servicios', icono: ICONOS.servicios,
    opciones: [
      { ruta: '/servicios/ordenes', texto: 'Órdenes de trabajo', icono: ICONOS.ordenes },
      { ruta: '/postventa', texto: 'Postventa', icono: ICONOS.postventa },
      { ruta: '/servicios/equipos', texto: 'Equipos instalados', icono: ICONOS.equipos },
      { ruta: '/servicios/contratos', texto: 'Contratos', icono: ICONOS.contratos },
    ],
  },
  {
    id: 'inventario', titulo: 'Inventario', icono: ICONOS.inventario,
    opciones: [
      { ruta: '/compras', texto: 'Compras', icono: ICONOS.compras },
      { ruta: '/catalogo/productos', texto: 'Productos', icono: ICONOS.productos },
      { ruta: '/catalogo/categorias', texto: 'Categorías', icono: ICONOS.categorias },
      { ruta: '/catalogo/productos/nuevo', texto: 'Nuevo producto', icono: ICONOS.nuevo, permiso: 'CATALOGO' },
    ],
  },
  {
    id: 'contactos', titulo: 'Contactos', icono: ICONOS.contactos,
    opciones: [
      { ruta: '/clientes', texto: 'Clientes', icono: ICONOS.clientes },
      { ruta: '/proveedores', texto: 'Proveedores', icono: ICONOS.proveedores },
    ],
  },
];

/** Grupo al que pertenece una URL (el de la ruta más larga que la contiene; Inicio si ninguna). */
export function grupoDeUrl(url: string): GrupoMenu {
  const ruta = url.split('?')[0].split('#')[0];
  let mejor: { grupo: GrupoMenu; largo: number } | null = null;
  for (const g of GRUPOS) {
    if (g.id === 'inicio') continue;
    for (const o of g.opciones) {
      for (const prefijo of [o.ruta, ...(o.prefijos ?? [])]) {
        if ((ruta === prefijo || ruta.startsWith(prefijo + '/')) && (!mejor || prefijo.length > mejor.largo)) {
          mejor = { grupo: g, largo: prefijo.length };
        }
      }
    }
  }
  return mejor?.grupo ?? GRUPOS[0];
}

/** Iniciales para avatares ("Lisandro Madrid" → "LM", "[Prueba] Rosa Quispe" → "RQ"). */
export function iniciales(nombre: string | null | undefined): string {
  return (nombre ?? '')
    .replace(/\[[^\]]*\]/g, '')
    .split(/\s+/)
    .filter((p) => /[A-Za-zÁÉÍÓÚÑáéíóúñ]/.test(p))
    .slice(0, 2)
    .map((p) => p[0].toUpperCase())
    .join('') || '?';
}

/** Tono de avatar estable por nombre (escala de verdes y neutros del tema). */
export function tonoAvatar(nombre: string | null | undefined): string {
  const tonos = ['#102A43', '#0078B7', '#0096C8', '#033E8A', '#03045E', '#0B5C8E', '#00759F'];
  let h = 0;
  for (const c of nombre ?? '') h = (h * 31 + c.charCodeAt(0)) >>> 0;
  return tonos[h % tonos.length];
}
