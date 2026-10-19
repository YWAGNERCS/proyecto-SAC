import { Component, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { filter } from 'rxjs';
import { AuthService } from '../auth/auth.service';

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './layout.html',
  styleUrl: './layout.css',
})
export class Layout {
  readonly auth = inject(AuthService);

  /** Menú desplegable en pantallas angostas (celular y tablet). */
  readonly menuAbierto = signal(false);

  constructor() {
    // Al cambiar de pantalla el menú se cierra solo
    inject(Router).events.pipe(filter((e) => e instanceof NavigationEnd))
      .subscribe(() => this.menuAbierto.set(false));
  }

  alternarMenu(): void {
    this.menuAbierto.update((v) => !v);
  }

  cerrarMenu(): void {
    this.menuAbierto.set(false);
  }

  logout(): void {
    this.auth.logout();
  }
}
