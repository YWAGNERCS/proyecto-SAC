import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DecimalPipe } from '@angular/common';
import { AuthService, NOMBRE_PERMISO } from '../auth/auth.service';
import { DescuentoService } from '../services/descuento-service';
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
  imports: [DecimalPipe, RouterLink],
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

  constructor() {
    inject(DescuentoService).tope().subscribe({ next: (t) => this.tope.set(t) });
    this.cargarTipoCambio();
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
