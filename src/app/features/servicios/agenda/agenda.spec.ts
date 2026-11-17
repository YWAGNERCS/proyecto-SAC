import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Subject } from 'rxjs';
import { OrdenService } from '../orden/orden-service';
import { OrdenTrabajo } from '../orden/orden.model';
import { AgendaComponent } from './agenda';

describe('Agenda de técnicos', () => {
  it('muestra solo la respuesta de la última semana seleccionada', () => {
    const primera = new Subject<OrdenTrabajo[]>();
    const segunda = new Subject<OrdenTrabajo[]>();
    const consultas = [primera, segunda];
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: OrdenService, useValue: { agenda: () => consultas.shift() } },
      ],
    });
    const agenda = TestBed.createComponent(AgendaComponent).componentInstance;

    agenda.mover(1);
    segunda.next([]);
    primera.error({ error: { message: 'Error de una consulta anterior' } });

    expect(primera.observed).toBe(false);
    expect(agenda.error()).toBe('');
    expect(agenda.cargando()).toBe(false);
  });
});
