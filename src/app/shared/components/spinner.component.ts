import { Component, input } from '@angular/core';

@Component({
  selector: 'app-spinner',
  standalone: true,
  template: `
    <div class="spinner-container">
      <span class="spinner"></span>
      <p>{{ mensaje() }}</p>
    </div>
  `,
  styles: [`
    .spinner-container {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 12px;
      color: #666;
    }
    .spinner {
      width: 18px;
      height: 18px;
      border: 2px solid #ccc;
      border-top-color: #007bff;
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
    }
    @keyframes spin {
      to { transform: rotate(360deg); }
    }
  `]
})
export class SpinnerComponent {
  readonly mensaje = input<string>('Cargando...');
}
