import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, catchError, map, throwError } from 'rxjs';
import { ApiService } from './api-service';

/**
 * Descarga archivos del backend (Excel, PDF). Usa HttpClient para que el interceptor
 * agregue las credenciales; un enlace <a href> directo no las llevaría.
 */
@Injectable({ providedIn: 'root' })
export class DescargaService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiService);

  /** Descarga `ruta` (relativa a /api) y la guarda con `nombreArchivo`. Solo envía los filtros con valor. */
  descargar(ruta: string, nombreArchivo: string, filtros: Record<string, string | number | null | undefined> = {}): Observable<void> {
    let params = new HttpParams();
    for (const [clave, valor] of Object.entries(filtros)) {
      if (valor !== null && valor !== undefined && valor !== '') params = params.set(clave, valor);
    }
    return this.http.get(this.api.buildUrl(ruta), { params, responseType: 'blob' }).pipe(
      map((archivo) => {
        const url = URL.createObjectURL(archivo);
        const enlace = document.createElement('a');
        enlace.href = url;
        enlace.download = nombreArchivo;
        enlace.click();
        URL.revokeObjectURL(url);
      }),
    );
  }

  /**
   * Abre un PDF del backend en una pestaña nueva (vista previa), con las credenciales del interceptor.
   * La pestaña se abre antes de pedir el archivo para que el navegador no la bloquee como ventana emergente.
   */
  abrir(ruta: string): Observable<void> {
    const ventana = window.open('', '_blank');
    return this.http.get(this.api.buildUrl(ruta), { responseType: 'blob' }).pipe(
      map((archivo) => {
        const url = URL.createObjectURL(archivo);
        if (ventana) ventana.location.href = url;
        else window.location.href = url;
        setTimeout(() => URL.revokeObjectURL(url), 60_000);
      }),
      catchError((err) => {
        ventana?.close();
        return throwError(() => err);
      }),
    );
  }

  /** Fecha de hoy (aaaa-mm-dd) para los nombres de archivo. */
  hoy(): string {
    return new Date().toISOString().slice(0, 10);
  }
}
