import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DecimalPipe } from '@angular/common';
import { AuthService, NOMBRE_PERMISO } from '../auth/auth.service';
import { DescuentoService, TopesDescuento } from '../services/descuento-service';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { TipoCambio, TipoCambioService } from '../services/tipo-cambio-service';
import { PeriodoTablero, PreferenciasService, TamanoLetra, Tema } from '../services/preferencias-service';
import { ICONOS, iniciales } from '../layout/menu';
import { environment } from '../../../environments/environment';

/**
 * Configuración (el engranaje del riel): datos de la cuenta, preferencias personales de esta
 * computadora (tema, letra, tablero), tipo de cambio del día, administración (solo ADMIN: permisos
 * e historial de cambios) y ayuda.
 * Las contraseñas y los usuarios viven en Keycloak: desde aquí solo se enlaza a su consola.
 */
@Component({
  selector: 'app-configuracion',
  imports: [DecimalPipe, RouterLink, FormsModule],
  templateUrl: './configuracion.html',
  styleUrl: './configuracion.css',
})
export class ConfiguracionComponent {
  readonly auth = inject(AuthService);
  readonly preferencias = inject(PreferenciasService);
  readonly iconos = ICONOS;
  readonly iniciales = iniciales;

  /** Permisos que el dueño le delegó a este usuario, en palabras. */
  readonly permisosDelegados = computed(() => this.auth.permisos().map((p) => NOMBRE_PERMISO[p] ?? p));

  readonly temas: { valor: Tema; etiqueta: string }[] = [
    { valor: 'CLARO', etiqueta: 'Claro' },
    { valor: 'OSCURO', etiqueta: 'Oscuro' },
    { valor: 'SISTEMA', etiqueta: 'Como el sistema' },
  ];

  readonly tope = signal<number | null>(null);
  readonly tipoCambio = signal<TipoCambio | null>(null);
  readonly errorTipoCambio = signal(false);

  readonly periodos: { valor: PeriodoTablero; etiqueta: string }[] = [
    { valor: 'MES', etiqueta: 'Este mes' },
    { valor: 'MES_ANTERIOR', etiqueta: 'Mes anterior' },
    { valor: 'TREINTA', etiqueta: 'Últimos 30 días' },
    { valor: 'TRIMESTRE', etiqueta: 'Este trimestre' },
    { valor: 'ANIO', etiqueta: 'Este año' },
  ];

  /** Consola de cuenta de Keycloak: el propio usuario cambia su contraseña. */
  readonly urlCuenta = `${environment.keycloak.issuer}/account`;
  /** Consola de administración del realm: usuarios, roles y contraseñas (solo ADMIN). */
  readonly urlAdministracion = environment.keycloak.issuer.replace(/\/realms\/([^/]+)$/, '/admin/$1/console/');
  readonly urlDocumentacion = 'https://ywagnercs.github.io/proyecto-SAC/';
  readonly urlManual = 'https://ywagnercs.github.io/proyecto-SAC/manual/';

  private readonly descuentoService = inject(DescuentoService);

  // -- Topes de descuento: solo los cambia el administrador (Lisandro), no se delegan --
  readonly topes = signal<TopesDescuento | null>(null);
  topeAdmin: number | null = null;
  topeAsistente: number | null = null;
  readonly guardandoTopes = signal(false);
  readonly mensajeTopes = signal('');
  readonly errorTopes = signal('');

  constructor() {
    this.descuentoService.tope().subscribe({ next: (t) => this.tope.set(t) });
    if (this.auth.tieneRol('ADMIN')) this.cargarTopes();
    this.cargarTipoCambio();
  }

  /** "08/11/2026 10:35" del último cambio de los topes. */
  fechaCambioTopes(): string {
    const f = this.topes()?.fechaActualizacion;
    return f ? `${f.slice(8, 10)}/${f.slice(5, 7)}/${f.slice(0, 4)} ${f.slice(11, 16)}` : '';
  }

  private cargarTopes() {
    this.descuentoService.topes().subscribe({
      next: (t) => {
        this.topes.set(t);
        this.topeAdmin = Number(t.topeAdmin);
        this.topeAsistente = Number(t.topeAsistente);
      },
    });
  }

  guardarTopes() {
    this.mensajeTopes.set('');
    this.errorTopes.set('');
    const admin = Number(this.topeAdmin);
    const asistente = Number(this.topeAsistente);
    if (this.topeAdmin === null || this.topeAsistente === null
        || !(admin >= 0 && admin <= 100) || !(asistente >= 0 && asistente <= 100)) {
      this.errorTopes.set('Cada tope debe estar entre 0 % y 100 %.');
      return;
    }
    if (asistente > admin) {
      this.errorTopes.set('El tope del asistente no puede ser mayor que el del administrador.');
      return;
    }
    this.guardandoTopes.set(true);
    this.descuentoService.cambiarTopes(admin, asistente).subscribe({
      next: (t) => {
        this.guardandoTopes.set(false);
        this.topes.set(t);
        this.mensajeTopes.set('Topes guardados. Rigen desde la próxima proforma o venta y quedaron en el historial de cambios.');
        this.descuentoService.tope().subscribe({ next: (x) => this.tope.set(x) });
      },
      error: (err: HttpErrorResponse) => {
        this.guardandoTopes.set(false);
        this.errorTopes.set(err.status === 403 ? 'Solo el administrador puede cambiar los topes.'
          : err.error?.message ?? 'No se pudieron guardar los topes. Inténtelo de nuevo.');
      },
    });
  }

  private readonly tipoCambioService = inject(TipoCambioService);

  cargarTipoCambio() {
    this.errorTipoCambio.set(false);
    this.tipoCambioService.hoy().subscribe({
      next: (tc) => this.tipoCambio.set(tc),
      error: () => this.errorTipoCambio.set(true),
    });
  }

  cambiarPeriodo(valor: string) {
    this.preferencias.periodoTablero.set(valor as PeriodoTablero);
  }

  cambiarTema(valor: Tema) {
    this.preferencias.tema.set(valor);
  }

  cambiarLetra(valor: TamanoLetra) {
    this.preferencias.tamanoLetra.set(valor);
  }
}
