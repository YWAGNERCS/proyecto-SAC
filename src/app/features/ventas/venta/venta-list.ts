import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { AuthService } from '../../../core/auth/auth.service';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { VentaService } from './venta-service';
import { EstadoVenta, VentaResponse } from './venta.model';
import { DescargaService } from '../../../core/services/descarga-service';
import { DocumentoSunat, EstadoSunatVenta, FacturacionService } from '../../../core/services/facturacion-service';
import { CobranzaService } from '../../cobranza/cobro/cobranza-service';
import { IconoComponent } from '../../../core/layout/icono';
import { PaginadorComponent } from '../../../core/layout/paginador';
import { CamposOrden, Orden, OrdenComponent, ordenarLista, ordenParaServidor } from '../../../core/layout/orden';

/** Columnas ordenables (en "Pagos atrasados" se ordena en el navegador; en el reporte, el servidor). */
const CAMPOS_ORDEN: CamposOrden<VentaResponse> = {
  id: (v) => v.id,
  cliente: (v) => v.clienteNombre,
  fecha: (v) => v.fechaVenta,
  estado: (v) => v.estado,
  // En soles, para que US$ 20 000 quede por encima de S/ 66 000 (igual que el orden del servidor)
  total: (v) => Number(v.total) * (v.moneda === 'DOLARES' && v.tipoCambio ? Number(v.tipoCambio) : 1),
};

interface Agregado {
  moneda: 'SOLES' | 'DOLARES';
  cantidad: number;
  montoTotal: number;
  ticketPromedio: number;
}

@Component({
  selector: 'app-venta-list',
  standalone: true,
  imports: [PaginadorComponent, OrdenComponent, IconoComponent, CommonModule, FormsModule, RouterLink],
  templateUrl: './venta-list.html',
  styleUrl: './venta-list.css',
})
export class VentaListComponent implements OnInit {
  /** Solo ADMIN modifica/elimina/anula (el backend lo vuelve a exigir con 403). */
  private readonly auth = inject(AuthService);
  /** ADMIN o asistente con el permiso ANULAR_VENTAS delegado (el backend exige lo mismo). */
  readonly puedeAnular = computed(() => this.auth.puede('ANULAR_VENTAS'));

  private ventaService = inject(VentaService);
  private descargaService = inject(DescargaService);
  private facturacion = inject(FacturacionService);
  private cobranzaService = inject(CobranzaService);
  exportando = signal(false);

  ventas = signal<VentaResponse[]>([]);
  /** Columna por la que se ordena (clic en el encabezado); null = la más nueva primero. */
  orden = signal<Orden | null>(null);
  /** En el reporte el servidor ya ordenó todas las páginas; la lista de atrasadas llega completa y se ordena aquí. */
  readonly ventasVisibles = computed(() =>
    this.vista() === 'ATRASADAS' ? ordenarLista(this.ventas(), this.orden(), CAMPOS_ORDEN) : this.ventas());
  /** Estado ante SUNAT del comprobante (y nota de crédito) de cada venta de la página. */
  private estadosSunat = signal(new Map<number, EstadoSunatVenta>());
  // Paginación del reporte (en "Pagos atrasados" se muestra la lista completa, que es corta)
  pagina = signal(0);
  tamano = signal(20);
  total = signal(0);
  totalPaginas = signal(0);
  /** Totales del filtro completo que calcula el servidor (en la vista de atrasadas se calculan aquí). */
  private resumenServidor = signal<Agregado[] | null>(null);
  private anuladasServidor = signal<number | null>(null);
  vista = signal<'REPORTE' | 'ATRASADAS'>('REPORTE');
  loading = signal(false);
  error = signal('');
  mensaje = signal('');
  /** Tras anular una venta ya pagada: muestra el enlace a Cobranza › Devoluciones. */
  devolucionPendiente = signal(false);

  // Filtros: se aplican solo al pulsar "Buscar" (no en cada cambio de fecha)
  estado: EstadoVenta | '' = '';
  desde = '';
  hasta = '';

  /**
   * Agregados calculados en el navegador a partir de los totales que ya calculó el servidor.
   * Solo cuentan las ventas REGISTRADAS (una anulada no es ingreso) y se separan por moneda.
   */
  readonly agregados = computed<Agregado[]>(() => {
    const delServidor = this.resumenServidor();
    if (delServidor) return delServidor;
    const vigentes = this.ventas().filter((v) => v.estado === 'REGISTRADA');
    return (['SOLES', 'DOLARES'] as const)
      .map((moneda) => {
        const ventas = vigentes.filter((v) => v.moneda === moneda);
        const montoTotal = ventas.reduce((suma, v) => suma + Number(v.total), 0);
        return {
          moneda,
          cantidad: ventas.length,
          montoTotal,
          ticketPromedio: ventas.length ? montoTotal / ventas.length : 0,
        };
      })
      .filter((a) => a.cantidad > 0);
  });

  readonly cantidadAnuladas = computed(
    () => this.anuladasServidor() ?? this.ventas().filter((v) => v.estado === 'ANULADA').length,
  );

  ngOnInit() {
    // Aviso que deja otra pantalla al navegar aquí (p. ej. "Convertir en venta" de una proforma).
    const aviso = history.state?.mensaje;
    if (typeof aviso === 'string') this.mensaje.set(aviso);
    this.buscar();
  }

  buscar() {
    this.error.set('');
    if (this.desde && this.hasta && this.desde > this.hasta) {
      this.error.set('La fecha "desde" no puede ser posterior a "hasta".');
      return;
    }
    this.vista.set('REPORTE');
    this.pagina.set(0);
    this.cargarPagina();
  }

  ordenar(o: Orden | null) {
    this.orden.set(o);
    if (this.vista() === 'REPORTE') {
      this.pagina.set(0);
      this.cargarPagina();
    }
  }

  irAPagina(p: number) {
    this.pagina.set(p);
    this.cargarPagina();
  }

  cambiarTamano(t: number) {
    this.tamano.set(t);
    this.pagina.set(0);
    this.cargarPagina();
  }

  private cargarPagina() {
    this.loading.set(true);
    this.ventaService.reportePagina({ estado: this.estado, desde: this.desde, hasta: this.hasta }, this.pagina(), this.tamano(),
        ordenParaServidor(this.orden()))
      .subscribe({
        next: (r) => {
          this.ventas.set(r.ventas.contenido);
          this.cargarEstadosSunat();
          this.total.set(r.ventas.total);
          this.totalPaginas.set(r.ventas.totalPaginas);
          this.resumenServidor.set(r.resumen);
          this.anuladasServidor.set(r.anuladas);
          this.loading.set(false);
        },
        error: (err: HttpErrorResponse) => {
          this.loading.set(false);
          this.error.set(err.error?.message ?? 'No se pudo cargar el reporte de ventas.');
        },
      });
  }

  limpiar() {
    this.estado = '';
    this.desde = '';
    this.hasta = '';
    this.buscar();
  }

  verAtrasadas() {
    this.vista.set('ATRASADAS');
    this.resumenServidor.set(null);
    this.anuladasServidor.set(null);
    this.cargar(this.ventaService.listarPagosAtrasados());
  }

  private recargar() {
    if (this.vista() === 'ATRASADAS') this.verAtrasadas();
    else this.cargarPagina();   // se queda en la misma página
  }

  private cargar(peticion: ReturnType<VentaService['listar']>) {
    this.loading.set(true);
    peticion.subscribe({
      next: (data) => {
        this.ventas.set(data);
        this.cargarEstadosSunat();
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(err.error?.message ?? 'No se pudo cargar el reporte de ventas.');
      },
    });
  }

  /** Antes de confirmar se consulta cuánto pagó el cliente: si anula, hay que devolvérselo (auditoría A8). */
  anular(v: VentaResponse) {
    this.error.set('');
    this.cobranzaService.pagadoDeVenta(v.id).subscribe({
      next: (p) => this.confirmarAnulacion(v, Number(p.pagado)),
      error: (err: HttpErrorResponse) =>
        this.error.set(err.status === 404 ? 'La venta ya no existe.' : 'No se pudo consultar lo pagado de la venta. Inténtalo de nuevo.'),
    });
  }

  private confirmarAnulacion(v: VentaResponse, pagado: number) {
    const monto = (n: number) => `${this.simbolo(v.moneda)} ${n.toFixed(2)}`;
    const aviso = pagado > 0
      ? `\n\nAtención: el cliente ya pagó ${monto(pagado)} de esta venta. Ese monto quedará como devolución pendiente ` +
        `en Cobranza › Devoluciones, donde se registra cuándo y cómo se le devolvió.`
      : '';
    const ok = confirm(
      `¿Anular la venta #${v.id} de ${v.clienteNombre} por ${monto(Number(v.total))}?\n\n` +
        `Se restaurará el stock de sus materiales, se cancelarán sus mantenimientos pendientes
` +
        `y se emitirá una nota de crédito por su comprobante. Esta acción no se puede deshacer.` + aviso,
    );
    if (!ok) return;
    this.ejecutar(
      this.ventaService.anular(v.id),
      `Venta #${v.id} anulada: stock restaurado, mantenimientos cancelados y nota de crédito emitida.` +
        (pagado > 0 ? ` Queda por devolver ${monto(pagado)} a ${v.clienteNombre}.` : ''),
      pagado > 0,
    );
  }

  private ejecutar(peticion: ReturnType<VentaService['anular']>, exito: string, conDevolucion = false) {
    this.error.set('');
    this.mensaje.set('');
    this.devolucionPendiente.set(false);
    peticion.subscribe({
      next: () => {
        this.mensaje.set(exito);
        this.devolucionPendiente.set(conDevolucion);
        this.recargar();
      },
      error: (err: HttpErrorResponse) => {
        // 409: la transición ya no es válida (p. ej. otra persona la anuló antes)
        this.error.set(
          err.status === 409
            ? `No se puede realizar la operación: ${err.error?.message}`
            : err.status === 404
              ? 'La venta ya no existe.'
              : 'Error inesperado. Inténtalo de nuevo.',
        );
        this.recargar();
      },
    });
  }

  /** Excel con los mismos filtros del reporte (hojas "Ventas" y "Detalle"). */
  exportarExcel() {
    this.exportando.set(true);
    this.descargaService
      .descargar('/v1/admin/ventas/exportar', `ventas_${this.descargaService.hoy()}.xlsx`,
        { estado: this.estado, desde: this.desde, hasta: this.hasta })
      .subscribe({
        next: () => this.exportando.set(false),
        error: () => {
          this.exportando.set(false);
          this.error.set('No se pudo generar el Excel de ventas.');
        },
      });
  }

  /** Pide el estado SUNAT de los comprobantes de las ventas mostradas (Facturación es otro módulo). */
  private cargarEstadosSunat() {
    const ids = this.ventas().filter((v) => v.comprobanteId).map((v) => v.id);
    if (!ids.length) {
      this.estadosSunat.set(new Map());
      return;
    }
    this.facturacion.estados(ids).subscribe({
      next: (lista) => this.estadosSunat.set(new Map(lista.map((e) => [e.ventaId, e]))),
      // Sin estado SUNAT la lista sigue sirviendo: no se muestra error
      error: () => this.estadosSunat.set(new Map()),
    });
  }

  sunat(v: VentaResponse): EstadoSunatVenta | undefined {
    return this.estadosSunat().get(v.id);
  }

  etiquetaSunat(d: DocumentoSunat): string {
    if (d.simulado) return 'Simulado (sin envío a SUNAT)';
    switch (d.estado) {
      case 'ACEPTADO': return 'Aceptado por SUNAT';
      case 'OBSERVADO': return 'Aceptado con observaciones';
      case 'RECHAZADO': return 'Rechazado por SUNAT';
      default: return 'Pendiente de envío';
    }
  }

  /** Comprobante y, si existe, nota de crédito de la venta, para la línea "Comprobante electrónico". */
  documentosSunat(s: EstadoSunatVenta): { d: DocumentoSunat; esNota: boolean; titulo: string }[] {
    const docs = [{ d: s.comprobante, esNota: false, titulo: 'Comprobante' }];
    if (s.notaCredito) docs.push({ d: s.notaCredito, esNota: true, titulo: 'Nota de crédito' });
    return docs;
  }

  etiquetaCorta(d: DocumentoSunat): string {
    if (d.simulado) return 'SUNAT: simulado';
    return { ACEPTADO: 'SUNAT: aceptado', OBSERVADO: 'SUNAT: observado', RECHAZADO: 'SUNAT: rechazado', PENDIENTE: 'SUNAT: pendiente' }[d.estado];
  }

  claseSunat(d: DocumentoSunat): string {
    if (d.simulado) return 'badge-neutro';
    const estado = d.estado;
    return estado === 'ACEPTADO' ? 'badge-success' : estado === 'RECHAZADO' ? 'badge-danger' : 'badge-warning';
  }

  /** Se puede reenviar lo rechazado (ya corregido) y lo pendiente que tiene un aviso o agotó los reintentos. */
  puedeReenviar(d: DocumentoSunat): boolean {
    return d.estado === 'RECHAZADO' || (d.estado === 'PENDIENTE' && d.intentos > 0);
  }

  reenviarSunat(d: DocumentoSunat, esNota: boolean) {
    this.error.set('');
    this.facturacion.reenviar(d.id, esNota).subscribe({
      next: () => {
        this.mensaje.set(`${d.numero} se volvió a enviar a SUNAT. El estado se actualiza en unos segundos.`);
        setTimeout(() => this.cargarEstadosSunat(), 4000);
      },
      error: (err: HttpErrorResponse) => this.error.set(err.error?.message ?? `No se pudo reenviar ${d.numero}.`),
    });
  }

  /** XML firmado o constancia de recepción (CDR) guardados del envío a SUNAT. */
  descargarSunat(d: DocumentoSunat, esNota: boolean, archivo: 'xml' | 'cdr') {
    const nombre = archivo === 'xml' ? `${d.numero}.xml` : `CDR_${d.numero}.zip`;
    this.descargaService.descargar(this.facturacion.rutaArchivo(d.id, esNota, archivo), nombre).subscribe({
      error: () => this.error.set(`No se pudo descargar el ${archivo === 'xml' ? 'XML' : 'CDR'} de ${d.numero}.`),
    });
  }

  /** PDF de la factura o boleta de la venta. */
  descargarComprobante(v: VentaResponse) {
    this.descargaService.descargar(`/v1/admin/facturacion/ventas/${v.id}/pdf`, `comprobante_venta_${v.id}.pdf`).subscribe({
      error: () => this.error.set(`No se pudo descargar el comprobante de la venta #${v.id}.`),
    });
  }

  simbolo(moneda: string): string {
    return moneda === 'DOLARES' ? 'US$' : 'S/';
  }

  getEstadoPagoClass(estado: string): string {
    switch (estado) {
      case 'PAGADO': return 'badge-success';
      case 'ATRASADO': return 'badge-danger';
      default: return 'badge-warning';
    }
  }

  getTipoPagoLabel(tipo: string): string {
    return tipo === 'CONTADO' ? 'Contado' : 'Crédito';
  }
}
