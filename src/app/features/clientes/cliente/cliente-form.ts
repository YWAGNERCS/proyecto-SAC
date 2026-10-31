import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ClienteService } from './cliente-service';
import { Cliente } from './cliente.model';
import { SedesClienteComponent } from '../sede/sedes-cliente';
import { IconoComponent } from '../../../core/layout/icono';

type Tipo = Cliente['tipo'];

@Component({
  selector: 'app-cliente-form',
  imports: [IconoComponent, ReactiveFormsModule, RouterLink, SedesClienteComponent],
  templateUrl: './cliente-form.html',
})
export class ClienteFormComponent {
  private readonly fb = inject(FormBuilder);
  private readonly clienteService = inject(ClienteService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly id = signal<number | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly loading = signal(false);
  protected readonly buscando = signal(false);
  protected readonly avisoBusqueda = signal<string | null>(null);
  /** Si el documento buscado ya pertenece a un cliente registrado. */
  protected readonly yaRegistrado = signal<Cliente | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    tipo: ['EMPRESA' as Tipo],
    documento: ['', [Validators.required, Validators.pattern(/^\d{11}$/)]],
    nombreORazonSocial: ['', [Validators.required, Validators.maxLength(150)]],
    direccion: ['', [Validators.maxLength(200)]],
    telefono: ['', [Validators.maxLength(20)]],
    correo: ['', [Validators.email, Validators.maxLength(100)]],
    agenteRetencion: [false],
  });

  private readonly tipo = toSignal(this.form.controls.tipo.valueChanges, { initialValue: this.form.controls.tipo.value });
  protected readonly esEmpresa = computed(() => this.tipo() === 'EMPRESA');
  protected readonly etiquetaDocumento = computed(() => (this.esEmpresa() ? 'RUC' : 'DNI'));
  protected readonly digitos = computed(() => (this.esEmpresa() ? 11 : 8));

  constructor() {
    // El documento cambia de formato según el tipo: RUC (11) o DNI (8)
    this.form.controls.tipo.valueChanges.subscribe((tipo) => {
      const doc = this.form.controls.documento;
      doc.setValidators([Validators.required, Validators.pattern(tipo === 'EMPRESA' ? /^\d{11}$/ : /^\d{8}$/)]);
      doc.updateValueAndValidity();
      this.avisoBusqueda.set(null);
      this.yaRegistrado.set(null);
    });

    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      const id = Number(idParam);
      this.id.set(id);
      this.loading.set(true);
      this.clienteService.obtener(id).subscribe({
        next: (c) => {
          this.form.patchValue({
            tipo: c.tipo,
            documento: (c.tipo === 'EMPRESA' ? c.ruc : c.dni) ?? '',
            nombreORazonSocial: c.nombreORazonSocial,
            direccion: c.direccion ?? '',
            telefono: c.telefono ?? '',
            correo: c.correo ?? '',
            agenteRetencion: !!c.agenteRetencion,
          });
          this.loading.set(false);
        },
        error: () => {
          this.error.set('No se pudo cargar el cliente.');
          this.loading.set(false);
        },
      });
    }
  }

  /** Consulta SUNAT (RUC) o RENIEC (DNI) y llena nombre y dirección. No guarda nada. */
  buscarDocumento(): void {
    const doc = this.form.controls.documento;
    doc.setValue(doc.value.trim());
    doc.markAsTouched();
    if (doc.invalid) return;

    this.buscando.set(true);
    this.avisoBusqueda.set(null);
    this.yaRegistrado.set(null);
    this.error.set(null);
    const consulta = this.esEmpresa()
      ? this.clienteService.autocompletarRuc(doc.value)
      : this.clienteService.autocompletarDni(doc.value);

    consulta.subscribe({
      next: (datos) => {
        this.buscando.set(false);
        if (datos.id && datos.id !== this.id()) {
          this.yaRegistrado.set(datos);
          return;
        }
        this.form.patchValue({
          nombreORazonSocial: datos.nombreORazonSocial ?? '',
          direccion: datos.direccion ?? this.form.controls.direccion.value,
        });
        this.avisoBusqueda.set(`Datos obtenidos de ${this.esEmpresa() ? 'SUNAT' : 'RENIEC'}. Revíselos y complete el contacto.`);
      },
      error: (err: HttpErrorResponse) => {
        this.buscando.set(false);
        const tipo = this.etiquetaDocumento();
        this.error.set(
          err.status === 404
            ? `No se encontró el ${tipo} ${doc.value}. Verifique el número o ingrese los datos a mano.`
            : err.status === 503
              ? 'El servicio de consulta está ocupado. Espere unos segundos y vuelva a buscar.'
              : err.status === 400
                ? err.error?.message ?? `${tipo} inválido.`
                : 'No se pudo consultar el documento. Puede ingresar los datos a mano.',
        );
      },
    });
  }

  guardar(): void {
    if (this.loading()) return;
    this.error.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const cliente: Cliente = {
      tipo: v.tipo,
      ruc: v.tipo === 'EMPRESA' ? v.documento : null,
      dni: v.tipo === 'PERSONA_NATURAL' ? v.documento : null,
      nombreORazonSocial: v.nombreORazonSocial.trim(),
      direccion: v.direccion.trim() || null,
      telefono: v.telefono.trim() || null,
      correo: v.correo.trim() || null,
      // Solo una empresa puede ser agente de retención
      agenteRetencion: v.tipo === 'EMPRESA' && v.agenteRetencion,
    };

    const id = this.id();
    const peticion = id ? this.clienteService.actualizar(id, cliente) : this.clienteService.crear(cliente);
    this.loading.set(true);
    peticion.subscribe({
      next: () => this.router.navigate(['/clientes']),
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        // 400: datos inválidos (documento, correo) · 409: RUC/DNI ya registrado
        this.error.set(
          err.status === 400 || err.status === 409
            ? err.error?.message ?? 'Datos inválidos.'
            : 'No se pudo guardar el cliente.',
        );
      },
    });
  }

  protected mensajeValidacion(campo: keyof typeof this.form.controls): string {
    const c = this.form.controls[campo];
    if (!c.touched || c.valid) return '';
    if (c.hasError('required')) return 'Este campo es obligatorio.';
    if (c.hasError('pattern')) return `El ${this.etiquetaDocumento()} debe tener ${this.digitos()} dígitos.`;
    if (c.hasError('email')) return 'Correo no válido.';
    if (c.hasError('maxlength')) return `Máximo ${c.getError('maxlength').requiredLength} caracteres.`;
    return '';
  }
}
