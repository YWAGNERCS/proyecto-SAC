import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ProveedorService } from './proveedor-service';
import { Proveedor } from './proveedor.model';

type Tipo = Proveedor['tipo'];

@Component({
  selector: 'app-proveedor-form',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './proveedor-form.html',
})
export class ProveedorFormComponent {
  private readonly fb = inject(FormBuilder);
  private readonly proveedorService = inject(ProveedorService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly id = signal<number | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly loading = signal(false);
  protected readonly buscando = signal(false);
  protected readonly avisoBusqueda = signal<string | null>(null);
  /** Si el documento buscado ya pertenece a un proveedor registrado. */
  protected readonly yaRegistrado = signal<Proveedor | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    tipo: ['EMPRESA' as Tipo],
    documento: ['', [Validators.required, Validators.pattern(/^\d{11}$/)]],
    nombreORazonSocial: ['', [Validators.required, Validators.maxLength(150)]],
    direccion: ['', [Validators.maxLength(200)]],
    telefono: ['', [Validators.maxLength(20)]],
    correo: ['', [Validators.email, Validators.maxLength(100)]],
    contactoVendedor: ['', [Validators.maxLength(150)]],
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
      this.proveedorService.obtener(id).subscribe({
        next: (p) => {
          this.form.patchValue({
            tipo: p.tipo,
            documento: (p.tipo === 'EMPRESA' ? p.ruc : p.dni) ?? '',
            nombreORazonSocial: p.nombreORazonSocial,
            direccion: p.direccion ?? '',
            telefono: p.telefono ?? '',
            correo: p.correo ?? '',
            contactoVendedor: p.contactoVendedor ?? '',
          });
          this.loading.set(false);
        },
        error: () => {
          this.error.set('No se pudo cargar el proveedor.');
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
      ? this.proveedorService.autocompletarRuc(doc.value)
      : this.proveedorService.autocompletarDni(doc.value);

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
    const proveedor: Proveedor = {
      tipo: v.tipo,
      ruc: v.tipo === 'EMPRESA' ? v.documento : undefined,
      dni: v.tipo === 'PERSONA_NATURAL' ? v.documento : undefined,
      nombreORazonSocial: v.nombreORazonSocial.trim(),
      direccion: v.direccion.trim() || undefined,
      telefono: v.telefono.trim() || undefined,
      correo: v.correo.trim() || undefined,
      contactoVendedor: v.contactoVendedor.trim() || undefined,
    };

    const id = this.id();
    const peticion = id ? this.proveedorService.actualizar(id, proveedor) : this.proveedorService.crear(proveedor);
    this.loading.set(true);
    peticion.subscribe({
      next: () => this.router.navigate(['/proveedores']),
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        // 400: datos inválidos (documento, correo) · 409: RUC/DNI ya registrado
        this.error.set(
          err.status === 400 || err.status === 409
            ? err.error?.message ?? 'Datos inválidos.'
            : 'No se pudo guardar el proveedor.',
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
