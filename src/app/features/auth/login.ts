import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class LoginComponent {
  username = '';
  password = '';
  mostrarPassword = signal(false);
  cargando = signal(false);
  error = signal<string | null>(null);

  constructor(
    private auth: AuthService,
    private router: Router,
    private route: ActivatedRoute,
  ) {}

  togglePassword(): void {
    this.mostrarPassword.update((v) => !v);
  }

  onSubmit(): void {
    if (!this.username.trim() || !this.password) return;

    this.cargando.set(true);
    this.error.set(null);

    this.auth.login(this.username.trim(), this.password).subscribe({
      next: () => {
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') ?? '/';
        this.router.navigateByUrl(returnUrl);
      },
      error: (err) => {
        this.cargando.set(false);
        if (err?.status === 401 || err?.status === 403) {
          this.error.set('Usuario o contraseña incorrectos.');
        } else if (err?.status === 0) {
          this.error.set('No se pudo conectar con el servidor. ¿Está el backend encendido?');
        } else {
          this.error.set('Error inesperado. Intenta de nuevo.');
        }
      },
    });
  }
}
