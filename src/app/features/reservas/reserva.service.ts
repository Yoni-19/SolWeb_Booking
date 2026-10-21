import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { Reserva } from '../../core/models/reserva.model';

/**
 * MOCK DATA — in-memory replacement for a future real HTTP service.
 * Method names mirror the REST endpoints (GET/POST /api/reservas...).
 */
@Injectable({ providedIn: 'root' })
export class ReservaService {
  private data: Reserva[] = [
    {
      id: 1, fechaHoraInicio: '2026-10-20T10:00', fechaHoraFin: '2026-10-20T12:00',
      estadoReserva: 'CONFIRMADA', montoTotal: 50000, canchaId: 1, canchaNombre: 'Cancha de Fútbol 1',
      usuarioId: 1, usuarioNombre: 'Juan Pérez',
    },
    {
      id: 2, fechaHoraInicio: '2026-10-20T14:00', fechaHoraFin: '2026-10-20T16:00',
      estadoReserva: 'PENDIENTE', montoTotal: 30000, canchaId: 3, canchaNombre: 'Cancha de Vóley',
      usuarioId: 2, usuarioNombre: 'María Gómez',
    },
    {
      id: 3, fechaHoraInicio: '2026-10-21T09:00', fechaHoraFin: '2026-10-21T11:00',
      estadoReserva: 'COMPLETADA', montoTotal: 40000, canchaId: 2, canchaNombre: 'Cancha de Futsal Central',
      usuarioId: 3, usuarioNombre: 'Carlos Ruiz',
    },
    {
      id: 4, fechaHoraInicio: '2026-10-22T18:00', fechaHoraFin: '2026-10-22T20:00',
      estadoReserva: 'CANCELADA', montoTotal: 50000, canchaId: 1, canchaNombre: 'Cancha de Fútbol 1',
      usuarioId: 4, usuarioNombre: 'Ana Torres',
    },
    {
      id: 5, fechaHoraInicio: '2026-10-23T16:00', fechaHoraFin: '2026-10-23T18:00',
      estadoReserva: 'PENDIENTE', montoTotal: 40000, canchaId: 2, canchaNombre: 'Cancha de Futsal Central',
      usuarioId: 5, usuarioNombre: 'Luis Fernández',
    },
  ];

  list(): Observable<Reserva[]> {
    return of(this.data.map((r) => ({ ...r })));
  }

  get(id: number): Observable<Reserva | undefined> {
    return of(this.data.find((r) => r.id === id));
  }

  create(reserva: Omit<Reserva, 'id'>): Observable<Reserva> {
    const nueva: Reserva = { ...reserva, id: Math.max(0, ...this.data.map((r) => r.id ?? 0)) + 1 };
    this.data.push(nueva);
    return of({ ...nueva });
  }

  update(reserva: Reserva): Observable<Reserva> {
    this.data = this.data.map((r) => (r.id === reserva.id ? { ...reserva } : r));
    return of({ ...reserva });
  }

  remove(id: number): Observable<void> {
    this.data = this.data.filter((r) => r.id !== id);
    return of(undefined);
  }
}
