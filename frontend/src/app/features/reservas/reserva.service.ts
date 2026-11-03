import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, of, throwError } from 'rxjs';
import { API_BASE } from '../../core/api.constants';
import { Pagina } from '../../core/models/pagina.model';
import { EstadoReserva, Reserva, ReservaAlta } from '../../core/models/reserva.model';

/**
 * Real HTTP service — talks to the Spring Boot backend.
 * The payload omits estadoReserva and montoTotal: the backend computes both.
 */
@Injectable({ providedIn: 'root' })
export class ReservaService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_BASE}/api/reservas`;

  /**
   * GET /api/reservas?page=&size=&estado= — server-side pagination.
   * A CLIENTE only receives their own rows; the backend applies that filter.
   */
  list(page = 0, size = 10, estado?: EstadoReserva): Observable<Pagina<Reserva>> {
    const params: Record<string, string | number> = { page, size, sort: 'id,desc' };
    if (estado) {
      params['estado'] = estado;
    }
    return this.http.get<Pagina<Reserva>>(this.url, { params });
  }

  get(id: number): Observable<Reserva | undefined> {
    return this.http.get<Reserva>(`${this.url}/${id}`).pipe(
      catchError((err: HttpErrorResponse) => (err.status === 404 ? of(undefined) : throwError(() => err))),
    );
  }

  create(reserva: ReservaAlta): Observable<Reserva> {
    return this.http.post<Reserva>(this.url, reserva);
  }

  update(id: number, reserva: ReservaAlta): Observable<Reserva> {
    return this.http.put<Reserva>(`${this.url}/${id}`, reserva);
  }

  remove(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }
}
