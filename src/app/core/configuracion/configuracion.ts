import { Component, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { AuthService } from '../auth/auth.service';
import { DescuentoService } from '../services/descuento-service';
import { TipoCambio, TipoCambioService } from '../services/tipo-cambio-service';
import { PeriodoTablero, PreferenciasService, TamanoLetra } from '../services/preferencias-service';
import { ICONOS, iniciales } from '../layout/menu';
import { environment } from '../../../environments/environment';

/**
 * Configuración (el engranaje del riel): datos de la cuenta, preferencias personales de esta
 * computadora, tipo de cambio del día, administración de usuarios (solo ADMIN) y ayuda.
 * Las contraseñas y los usuarios viven en Keycloak: desde aquí solo se enlaza a su consola.
 */
@Component({
  selector: 'app-configuracion',
  imports: [DecimalPipe],
  templateUrl: './configuracion.html',
  styleUrl: './configuracion.css',
})
export class ConfiguracionComponent {
  readonly auth = inject(AuthService);
  readonly preferencias = inject(PreferenciasService);
  readonly iconos = ICONOS;
  readonly iniciales = iniciales;

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

  cambiarLetra(valor: TamanoLetra) {
    this.preferencias.tamanoLetra.set(valor);
  }
}
