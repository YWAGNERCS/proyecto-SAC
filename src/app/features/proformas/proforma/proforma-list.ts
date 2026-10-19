import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ProformaService } from './proforma-service';
import { ClienteService } from '../../clientes/cliente/cliente-service';
import { VentaService } from '../../ventas/venta/venta-service';
import { ProformaResponse } from './proforma.model';
import { Cliente } from '../../clientes/cliente/cliente.model';

@Component({
  selector: 'app-proforma-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './proforma-list.html',
  styleUrl: './proforma-list.css'
})
export class ProformaListComponent implements OnInit {
  private proformaService = inject(ProformaService);
  private clienteService = inject(ClienteService);
  private ventaService = inject(VentaService);
  private router = inject(Router);

  proformas = signal<ProformaResponse[]>([]);
  clientes = signal<Cliente[]>([]);
  accionEnCurso = signal<number | null>(null);

  mensaje = signal('');
  error = signal('');

  // Diálogo "Convertir en venta"
  aConvertir = signal<ProformaResponse | null>(null);
  tipoPago: 'CONTADO' | 'CREDITO' = 'CONTADO';
  diasCredito = 30;
  convirtiendo = signal(false);
  errorConversion = signal('');

  ngOnInit() {
    this.cargarProformas();
    this.cargarClientes();
  }

  cargarProformas(clienteId?: number) {
    this.proformaService.listar(clienteId).subscribe({
      next: (data: any) => this.proformas.set(data)
    });
  }

  cargarClientes() {
    this.clienteService.listarTodos().subscribe({
      next: (data: any) => this.clientes.set(data)
    });
  }

  filtrarPorCliente(event: Event) {
    const select = event.target as HTMLSelectElement;
    const clienteId = select.value ? Number(select.value) : undefined;
    this.cargarProformas(clienteId);
  }

  /** Envía por WhatsApp y correo y muestra qué salió y qué no (el backend lo informa). */
  enviar(id: number) {
    this.limpiarAvisos();
    this.accionEnCurso.set(id);
    this.proformaService.enviar(id).subscribe({
      next: (r) => {
        this.accionEnCurso.set(null);
        const salioAlgo = r.correo === 'ENVIADO' || r.whatsapp === 'SIMULADO';
        const fallo = r.correo === 'FALLO' || r.correo === 'SIN_CORREO' || r.whatsapp === 'SIN_TELEFONO';
        if (salioAlgo && !fallo) this.mensaje.set(r.mensaje);
        else this.error.set(r.mensaje);
        this.cargarProformas();
      },
      error: (err: HttpErrorResponse) => this.fallo(err, 'No se pudo enviar la proforma.')
    });
  }

  aceptar(id: number) {
    this.limpiarAvisos();
    this.accionEnCurso.set(id);
    this.proformaService.aceptar(id).subscribe({
      next: () => {
        this.accionEnCurso.set(null);
        this.mensaje.set(`Proforma #${id} aceptada: ya puede convertirla en venta.`);
        this.cargarProformas();
      },
      error: (err: HttpErrorResponse) => this.fallo(err, 'No se pudo aceptar la proforma.')
    });
  }

  rechazar(c: ProformaResponse) {
    const cliente = c.clienteNombre || c.cliente?.nombreORazonSocial || '';
    if (!confirm(`¿Marcar la proforma #${c.id} de ${cliente} como RECHAZADA?\n\nEsta acción no se puede deshacer.`)) {
      return;
    }
    this.limpiarAvisos();
    this.accionEnCurso.set(c.id);
    this.proformaService.rechazar(c.id).subscribe({
      next: () => {
        this.accionEnCurso.set(null);
        this.mensaje.set(`Proforma #${c.id} marcada como rechazada.`);
        this.cargarProformas();
      },
      error: (err: HttpErrorResponse) => this.fallo(err, 'No se pudo rechazar la proforma.')
    });
  }

  private limpiarAvisos() {
    this.mensaje.set('');
    this.error.set('');
  }

  private fallo(err: HttpErrorResponse, porDefecto: string) {
    this.accionEnCurso.set(null);
    this.error.set(this.mensajeDeError(err) || porDefecto);
    this.cargarProformas();
  }

  abrirConversion(proforma: ProformaResponse) {
    this.tipoPago = 'CONTADO';
    this.diasCredito = 30;
    this.errorConversion.set('');
    this.aConvertir.set(proforma);
  }

  cerrarConversion() {
    if (!this.convirtiendo()) this.aConvertir.set(null);
  }

  fechaLimitePago(): string {
    const d = new Date();
    d.setDate(d.getDate() + (this.diasCredito || 0));
    return d.toLocaleDateString('es-PE');
  }

  confirmarConversion() {
    const proforma = this.aConvertir();
    if (!proforma) return;
    if (this.tipoPago === 'CREDITO' && !(this.diasCredito >= 1 && this.diasCredito <= 365)) {
      this.errorConversion.set('Los días de crédito deben estar entre 1 y 365.');
      return;
    }
    const dias = this.tipoPago === 'CREDITO' ? this.diasCredito : undefined;
    this.convirtiendo.set(true);
    this.errorConversion.set('');
    this.ventaService.convertirDesdeProforma(proforma.id, this.tipoPago, dias).subscribe({
      next: (venta) => {
        this.convirtiendo.set(false);
        this.aConvertir.set(null);
        const pago = dias ? `a crédito (${dias} días)` : 'al contado';
        this.router.navigate(['/ventas'], {
          state: { mensaje: `Proforma #${proforma.id} convertida en la venta #${venta.id}, ${pago}.` }
        });
      },
      error: (err: HttpErrorResponse) => {
        this.convirtiendo.set(false);
        this.errorConversion.set(this.mensajeDeError(err));
      }
    });
  }

  private mensajeDeError(err: HttpErrorResponse): string {
    if (err.status === 0) return 'No hay conexión con el servidor. Revise que el backend esté encendido.';
    const detalle = err.error?.message;
    if (detalle) return detalle;
    if (err.status === 409) return 'La proforma no se puede convertir (ya fue convertida, no está aceptada o falta stock).';
    if (err.status === 404) return 'La proforma ya no existe.';
    return 'No se pudo registrar la venta. Intente nuevamente.';
  }

  getEstadoClass(estado: string): string {
    switch (estado) {
      case 'ACEPTADA': return 'badge-success';
      case 'RECHAZADA': return 'badge-danger';
      case 'VENCIDA': return 'badge-warning';
      case 'CONVERTIDA': return 'badge-info';
      default: return '';
    }
  }
}

