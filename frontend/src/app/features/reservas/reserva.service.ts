import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, of, throwError } from 'rxjs';
import { API_BASE } from '../../core/api.constants';
import { Pagina } from '../../core/models/pagina.model';
import { EstadoReserva, FranjaOcupada, Reserva, ReservaAlta } from '../../core/models/reserva.model';

/**
 * Real HTTP service — talks to the Spring Boot backend.
 * The payload omits estadoReserva and montoTotal: the backend computes both.
 */
@Injectable({ providedIn: 'root' })
export class ReservaService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_BASE}/api/reservas`;

  /**
   * GET /api/reservas?page=&size=&estado=&fecha= — server-side pagination.
   * A CLIENTE only receives their own rows; the backend applies that filter.
   * `fecha` is a local "yyyy-MM-dd" that keeps only reservations starting that day.
   */
  list(page = 0, size = 10, estado?: EstadoReserva, fecha?: string): Observable<Pagina<Reserva>> {
    const params: Record<string, string | number> = { page, size, sort: 'id,desc' };
    if (estado) {
      params['estado'] = estado;
    }
    if (fecha) {
      params['fecha'] = fecha;
    }
    return this.http.get<Pagina<Reserva>>(this.url, { params });
  }

  /** GET /api/reservas/disponibilidad?canchaId=&fecha=yyyy-MM-dd — franjas ya tomadas. */
  disponibilidad(canchaId: number, fecha: string): Observable<FranjaOcupada[]> {
    return this.http.get<FranjaOcupada[]>(`${this.url}/disponibilidad`, {
      params: { canchaId, fecha },
    });
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

  /** PATCH /api/reservas/{id}/estado — check-in/out (staff) y cancelación (staff o cliente dueño). */
  cambiarEstado(id: number, estado: EstadoReserva): Observable<Reserva> {
    return this.http.patch<Reserva>(`${this.url}/${id}/estado`, { estadoReserva: estado });
  }
}
