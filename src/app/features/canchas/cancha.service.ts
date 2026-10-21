import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { Cancha } from '../../core/models/cancha.model';

/**
 * MOCK DATA — in-memory replacement for a future real HTTP service.
 * Method names mirror the REST endpoints (GET/POST /api/canchas...) so the
 * swap to HttpClient later only changes the method bodies.
 */
@Injectable({ providedIn: 'root' })
export class CanchaService {
  private data: Cancha[] = [
    { id: 1, nombre: 'Cancha de Fútbol 1', deporte: 'FUTBOL', precioPorHora: 25000, activa: true },
    { id: 2, nombre: 'Cancha de Futsal Central', deporte: 'FUTSAL', precioPorHora: 20000, activa: true },
    { id: 3, nombre: 'Cancha de Vóley', deporte: 'VOLEY', precioPorHora: 15000, activa: true },
    { id: 4, nombre: 'Cancha de Básquet', deporte: 'BASQUET', precioPorHora: 18000, activa: false },
  ];

  list(): Observable<Cancha[]> {
    return of(this.data.map((c) => ({ ...c })));
  }

  get(id: number): Observable<Cancha | undefined> {
    return of(this.data.find((c) => c.id === id));
  }

  create(cancha: Omit<Cancha, 'id'>): Observable<Cancha> {
    const nueva: Cancha = { ...cancha, id: Math.max(0, ...this.data.map((c) => c.id ?? 0)) + 1 };
    this.data.push(nueva);
    return of({ ...nueva });
  }

  update(cancha: Cancha): Observable<Cancha> {
    this.data = this.data.map((c) => (c.id === cancha.id ? { ...cancha } : c));
    return of({ ...cancha });
  }

  remove(id: number): Observable<void> {
    this.data = this.data.filter((c) => c.id !== id);
    return of(undefined);
  }
}
