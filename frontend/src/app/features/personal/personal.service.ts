import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, of, throwError } from 'rxjs';
import { API_BASE } from '../../core/api.constants';
import {
  Cliente,
  ClienteEdicion,
  PersonalAlta,
  RolPersonal,
} from '../../core/models/cliente.model';
import { Pagina } from '../../core/models/pagina.model';

/**
 * Gestión del personal. Usa el mismo recurso `/api/usuarios`, pero el listado
 * pide `personal=true` y el alta va por el endpoint exclusivo del ADMIN.
 */
@Injectable({ providedIn: 'root' })
export class PersonalService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_BASE}/api/usuarios`;

  /** GET /api/usuarios?personal=true — solo ADMIN; devuelve todo lo que no es CLIENTE. */
  list(page = 0, size = 10): Observable<Pagina<Cliente>> {
    return this.http.get<Pagina<Cliente>>(this.url, {
      params: { page, size, sort: 'id,desc', personal: 'true' },
    });
  }

  get(id: number): Observable<Cliente | undefined> {
    return this.http.get<Cliente>(`${this.url}/${id}`).pipe(
      catchError((err: HttpErrorResponse) =>
        err.status === 404 ? of(undefined) : throwError(() => err),
      ),
    );
  }

  /** POST /api/usuarios/personal — el rol es obligatorio y nunca puede ser CLIENTE. */
  create(personal: PersonalAlta): Observable<Cliente> {
    return this.http.post<Cliente>(`${this.url}/personal`, personal);
  }

  update(personal: Cliente, clave?: string): Observable<Cliente> {
    const payload: ClienteEdicion = {
      nombres: personal.nombres,
      apellidos: personal.apellidos,
      correo: personal.correo,
    };
    if (clave) {
      payload.clave = clave;
    }
    return this.http.put<Cliente>(`${this.url}/${personal.id}`, payload);
  }

  remove(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }

  /** PUT /api/usuarios/{id}/rol — solo ADMIN. */
  cambiarRol(id: number, rol: RolPersonal): Observable<Cliente> {
    return this.http.put<Cliente>(`${this.url}/${id}/rol`, { rol });
  }
}
