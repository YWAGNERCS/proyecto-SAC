import {
  ApplicationConfig,
  provideBrowserGlobalErrorListeners,
  provideZonelessChangeDetection,
} from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { routes } from './app.routes';
import { traceIdInterceptor } from './core/interceptors/trace-id-interceptor';
import { basicAuthInterceptor } from './core/interceptors/basic-auth.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZonelessChangeDetection(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(
      withInterceptors([
        traceIdInterceptor,    // agrega X-Trace-ID
        basicAuthInterceptor,  // agrega Authorization: Basic <token> en rutas /admin/
      ]),
    ),
  ],
};
